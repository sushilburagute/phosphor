import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';

const git = (...args) => execFileSync('git', args, { encoding: 'utf8' }).trimEnd();
const semver = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/;

function nextVersion(name, bump) {
  const index = ['major', 'minor', 'patch'].indexOf(bump);
  if (index < 0) throw Error('Choose patch, minor, or major.');
  if (!semver.test(name)) throw Error('Expected MAJOR.MINOR.PATCH in version.properties.');
  const parts = name.split('.').map(BigInt);
  parts[index] += 1n;
  parts.fill(0n, index + 1);
  return parts.join('.');
}

function commitNotes(previous) {
  const tag = git('tag', '--merged', 'HEAD', '--list', 'v' + previous);
  const start = tag || git('log', '--first-parent', '-1', '--format=%H', '--', 'version.properties');
  return git('log', '--reverse', '--no-merges', '--invert-grep', '--grep=^Publish phosphor-.*-debug$',
    '--format=- %s (%h)', start + '..HEAD', '--', '.', ':(exclude)releases');
}

function heading(section) {
  return section.split('\n', 1)[0].replace(/^## /, '').trim();
}

function body(section = '') {
  return section.split('\n').slice(1).join('\n').trim();
}

const props = readFileSync('version.properties', 'utf8');
const nameField = /^versionName=([^\s]+)[ \t]*$/m;
const codeField = /^versionCode=(\d+)[ \t]*$/m;
const previous = props.match(nameField)?.[1];
const version = nextVersion(previous, process.argv[2]);
const code = Number(props.match(codeField)?.[1]);
if (!Number.isInteger(code) || code < 1 || code >= 2_100_000_000) {
  throw Error('Cannot increment versionCode within the Android version code range.');
}

const changelog = readFileSync('CHANGELOG.md', 'utf8').replaceAll('\r\n', '\n');
const sections = changelog.split(/(?=^## )/m);
const unreleased = sections.findIndex(section => heading(section) === 'Unreleased');
if (unreleased < 0) throw Error('CHANGELOG.md needs an Unreleased section.');
const existing = sections.findIndex(section => heading(section) === version);
let notes = body(sections[existing]);
if (!notes) {
  notes = body(sections[unreleased]) || commitNotes(previous) || 'Release v' + version + '.';
  sections[unreleased] = '## Unreleased\n\n';
  const entry = '## ' + version + '\n\n' + notes + '\n\n';
  if (existing >= 0) sections[existing] = entry;
  else sections.splice(unreleased + 1, 0, entry);
}

writeFileSync('version.properties', props.replace(nameField, 'versionName=' + version)
  .replace(codeField, 'versionCode=' + (code + 1)));
writeFileSync('CHANGELOG.md', sections.join('').trimEnd() + '\n');
writeFileSync('release-notes.md', notes + '\n');
