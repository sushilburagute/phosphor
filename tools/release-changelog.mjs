import { execFileSync } from 'node:child_process';
import { appendFileSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const git = (...args) => execFileSync('git', args, { encoding: 'utf8' }).trimEnd();
const readChangelog = (ref) => git('show', ref + ':CHANGELOG.md').replaceAll('\r\n', '\n');
const semver = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/;

function compareVersions(a, b) {
  const other = b.split('.').map(BigInt);
  for (const [i, part] of a.split('.').map(BigInt).entries()) {
    if (part !== other[i]) return part > other[i] ? 1 : -1;
  }
  return 0;
}

function versionAt(ref) {
  const props = git('show', ref + ':version.properties');
  const name = props.match(/^versionName=(\S+)\s*$/m)?.[1];
  const code = props.match(/^versionCode=(\d+)\s*$/m)?.[1];
  if (!semver.test(name) || !code) throw Error('Invalid version.properties at ' + ref);
  return { name, code: BigInt(code) };
}

function sections(markdown) {
  return markdown.split(/(?=^## )/m);
}

function heading(section) {
  return section.split('\n', 1)[0].replace(/^## /, '').trim();
}

function body(section = '') {
  return section.split('\n').slice(1).join('\n').trim();
}

const { GITHUB_SHA: source, BEFORE_SHA: before, RUNNER_TEMP: temp } = process.env;
const notesFile = join(temp, 'release-notes.md');
const unreleasedFile = join(temp, 'released-unreleased.md');

function prepare() {
  if (/^0+$/.test(before)) return; // A newly created branch has no preceding version.
  const previous = versionAt(before);
  const current = versionAt(source);
  if (current.name === previous.name && current.code >= previous.code) return;
  if (compareVersions(current.name, previous.name) <= 0 || current.code <= previous.code) {
    throw Error('A new release requires a higher versionName and versionCode.');
  }
  const entries = sections(readChangelog(source));
  const existing = body(entries.find(s => heading(s) === current.name));
  const unreleased = existing ? '' : body(entries.find(s => heading(s) === 'Unreleased'));
  let notes = existing || unreleased;
  if (!notes) {
    const previousTag = 'refs/tags/v' + previous.name;
    const tags = git('tag', '--merged', before, '--list', 'v' + previous.name);
    const start = tags ? previousTag : git('log', '--first-parent', '-1', '--format=%H', before, '--', 'version.properties');
    notes = git('log', '--reverse', '--no-merges', '--invert-grep', '--grep=^Publish phosphor-.*-debug$',
      '--format=- %s (%h)', start + '..' + source,
      '--', '.', ':(exclude)releases');
  }
  if (!notes) throw Error('Could not generate release notes from the changelog or commit history.');
  writeFileSync(notesFile, notes + '\n');
  writeFileSync(unreleasedFile, unreleased);
  appendFileSync(process.env.GITHUB_ENV, 'RELEASE_VERSION=' + current.name + '\n');
}

function updateChangelog() {
  const version = process.env.RELEASE_VERSION;
  const entries = sections(readFileSync('CHANGELOG.md', 'utf8').replaceAll('\r\n', '\n'));
  const existing = entries.findIndex(s => heading(s) === version);
  if (existing >= 0) {
    if (body(entries[existing])) return;
    entries.splice(existing, 1);
  }
  const unreleased = entries.findIndex(s => heading(s) === 'Unreleased');
  if (unreleased < 0) throw Error('CHANGELOG.md needs an Unreleased section.');
  // A later push may have added notes while this release was building.
  const promoted = new Set(readFileSync(unreleasedFile, 'utf8').split('\n').filter(Boolean));
  const remaining = body(entries[unreleased]).split('\n').filter(line => !promoted.has(line)).join('\n').trim();
  entries[unreleased] = '## Unreleased\n\n' + (remaining ? remaining + '\n\n' : '');
  const older = entries.findIndex(s => semver.test(heading(s)) && compareVersions(heading(s), version) < 0);
  const notes = readFileSync(notesFile, 'utf8').trimEnd();
  entries.splice(older < 0 ? entries.length : older, 0, '## ' + version + '\n\n' + notes + '\n\n');
  writeFileSync('CHANGELOG.md', entries.join('').trimEnd() + '\n');
}

if (process.argv[2] === 'prepare') prepare();
else if (process.argv[2] === 'update') updateChangelog();
else throw Error('Expected prepare or update.');
