package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import dev.sush.phosphor.render.RenderKit.Companion.fraction
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Chromatic loop: a broad band twisting through space, lit like a sculpture. One face is
 * tinted with the first color, the other with the second, so every twist flips the band's
 * color; edges split into colored fringes that widen when the band is spun.
 *
 * The band is a closed strip of quads with a slightly curled cross-section. Each quad is
 * shaded per vertex (diffuse, specular, a Fresnel rim) and drawn back to front, so it occludes
 * itself correctly; anti-aliased edge strokes hide the mesh's hard edges.
 *
 * A swipe spins it on an invisible thread and it swings back; a held finger becomes the light
 * and winds the twist tighter, which springs loose on release.
 */
internal class AbstractLoop(private val k: RenderKit) {
    private val verts = FloatArray(RINGS * ROWS * 2)
    private val colors = IntArray(RINGS * ROWS)
    private val indices = ShortArray(RINGS * STRIPS * 6)
    private val depth = FloatArray(RINGS)
    private val order = IntArray(RINGS)
    private val ringX = FloatArray(RINGS)
    private val ringY = FloatArray(RINGS)
    private val backFace = BooleanArray(RINGS)
    private val edge = FloatArray(8)
    private val motes = Array(3) { FloatArray(MOTES * 2) }
    private val moteCount = IntArray(3)

    // Rotation and twist springs, only ever advanced by real frame time.
    private var spin = 0f
    private var spinV = 0f
    private var twist = 0f
    private var twistV = 0f

    // Scratch for the current vertex's shading.
    private var r = 0f
    private var g = 0f
    private var b = 0f

    init {
        var n = 0
        for (i in 0 until RINGS) {
            val j = (i + 1) % RINGS
            for (v in 0 until STRIPS) {
                val a = (i * ROWS + v).toShort(); val b = (i * ROWS + v + 1).toShort()
                val c = (j * ROWS + v + 1).toShort(); val d = (j * ROWS + v).toShort()
                indices[n++] = a; indices[n++] = b; indices[n++] = c
                indices[n++] = a; indices[n++] = c; indices[n++] = d
            }
        }
    }

    fun draw(canvas: Canvas, f: Frame) {
        val h = f.h
        val night = f.night
        val reveal = smooth(f.reveal)
        springs(f)

        val mode = k.modeFor(f)
        k.glow(canvas, k.glowA, 300f + f.shiftX(0.3f), h * 0.42f, 620f, h * 0.3f, if (night) 30 else 34, mode)
        k.glow(canvas, k.glowB, 720f + f.shiftX(0.3f), h * 0.66f, 560f, h * 0.26f, if (night) 26 else 30, mode)
        streak(canvas, f, reveal)
        dust(canvas, f, reveal, false)
        build(f, reveal)
        band(canvas, f, reveal)
        dust(canvas, f, reveal, true)
        touch(canvas, f)
    }

    private fun springs(f: Frame) {
        if (!f.busy && f.dt == 0f) {
            spin = 0f; spinV = 0f; twist = 0f; twistV = 0f
            return
        }
        val target = f.charge * f.drag * 2.8f
        if (f.dt == 0f) {
            twist = target
            return
        }
        val dt = f.dt
        // A torsion pendulum: swipes kick it, it swings back and settles.
        spinV += (-spin * 2.2f - spinV * 1.1f + f.flowX * 0.0011f) * dt
        spin += spinV * dt
        twistV += ((target - twist) * 16f - twistV * 2.4f) * dt
        twist += twistV * dt
    }

    // region Geometry and shading

    private fun build(f: Frame, reveal: Float) {
        val h = f.h
        val t = f.t
        val cx = 500f + f.shiftX(0.8f) + (f.dragX - 500f) * 0.05f * f.drag
        val cy = h * 0.55f + f.shiftY(0.8f)
        // Orientation: a slow yaw sway plus the spin spring and the live swipe.
        val yaw = 0.68f + sin(t * 0.09f) * 0.22f * f.amount + spin + f.flowX * 0.00022f
        val pitch = -0.5f + sin(t * 0.071f) * 0.08f * f.amount + f.flowY * 0.0001f
        val roll = -0.36f + sin(t * 0.053f) * 0.05f * f.amount
        val cyw = cos(yaw); val syw = sin(yaw)
        val cp = cos(pitch); val sp = sin(pitch)
        val cr = cos(roll); val sr = sin(roll)

        // Light from the upper left and in front; a held finger takes it over.
        var lx = -0.5f; var ly = -0.62f; var lz = -0.6f
        for (i in 0 until RINGS) {
            val u = i / RINGS.toFloat() * TAU
            val wobble = t * 0.12f
            // Centerline: an ellipse with a saddle undulation, so it never sits flat.
            val px = RX * cos(u)
            val py = RY * sin(u)
            val pz = RZ * sin(2f * u + wobble)
            var tx = -RX * sin(u); var ty = RY * cos(u); var tz = 2f * RZ * cos(2f * u + wobble)
            var len = sqrt(tx * tx + ty * ty + tz * tz)
            tx /= len; ty /= len; tz /= len
            // In-plane outward normal, then the binormal; re-orthogonalize.
            var nx = RY * cos(u); var ny = RX * sin(u); var nz = 0f
            var bx = ty * nz - tz * ny; var by = tz * nx - tx * nz; var bz = tx * ny - ty * nx
            len = sqrt(bx * bx + by * by + bz * bz); bx /= len; by /= len; bz /= len
            nx = by * tz - bz * ty; ny = bz * tx - bx * tz; nz = bx * ty - by * tx
            // Two full twists around the loop, drifting, plus the wound-up spring.
            val tau = 2f * u + t * 0.07f + twist * (0.5f + 0.5f * cos(u - 1.2f))
            val ct = cos(tau); val st = sin(tau)
            val wx = nx * ct + bx * st; val wy = ny * ct + by * st; val wz = nz * ct + bz * st
            val sx = bx * ct - nx * st; val sy = by * ct - ny * st; val sz = bz * ct - nz * st
            var half = 104f + 40f * sin(u * 3f + 1f + t * 0.11f)

            // Ring center in view space, for pulses and depth.
            val cxv = rotX(px, py, pz, cyw, syw, cp, sp, cr, sr)
            val cyv = rotY
            val czv = rotZ
            val sc = FOCAL / (czv + DIST)
            ringX[i] = cx + cxv * sc; ringY[i] = cy + cyv * sc
            depth[i] = czv
            val wave = if (f.pulses > 0) f.wave(ringX[i], ringY[i], 130f) else 0f
            half *= 1f + wave * 0.35f
            // The band unspools around the loop as the wallpaper wakes.
            val shown = smooth(((reveal * 1.3f - i / RINGS.toFloat() * 0.3f)).coerceIn(0f, 1f))
            half *= shown

            var back = false
            for (v in 0 until ROWS) {
                val w = v / (ROWS - 1f) * 2f - 1f
                val curl = (w * w - 0.33f) * CURL
                val ox = px + wx * w * half + sx * curl
                val oy = py + wy * w * half + sy * curl
                val oz = pz + wz * w * half + sz * curl
                val vx = rotX(ox, oy, oz, cyw, syw, cp, sp, cr, sr)
                val vy = rotY
                val vz = rotZ
                val s = FOCAL / (vz + DIST)
                val sxp = cx + vx * s
                val syp = cy + vy * s
                val at = i * ROWS + v
                verts[at * 2] = sxp; verts[at * 2 + 1] = syp
                // Curled normal: the sheet bends across its width like a gutter.
                var mx = sx - wx * w * 0.7f; var my = sy - wy * w * 0.7f; var mz = sz - wz * w * 0.7f
                len = sqrt(mx * mx + my * my + mz * mz); mx /= len; my /= len; mz /= len
                var qx = rotX(mx, my, mz, cyw, syw, cp, sp, cr, sr)
                var qy = rotY
                var qz = rotZ
                // Facing the camera (at -z) means a negative z normal; otherwise we see the back.
                if (v == ROWS / 2) back = qz > 0f
                if (back) { qx = -qx; qy = -qy; qz = -qz }
                if (f.drag > 0.01f) {
                    // The finger is a lamp hovering just above the glass.
                    var fx = f.dragX - sxp; var fy = f.dragY - syp; var fz = -260f
                    val fl = sqrt(fx * fx + fy * fy + fz * fz); fx /= fl; fy /= fl; fz /= fl
                    val m = f.drag
                    lx = -0.5f + (fx + 0.5f) * m; ly = -0.62f + (fy + 0.62f) * m; lz = -0.6f + (fz + 0.6f) * m
                }
                shade(f, qx, qy, qz, lx, ly, lz, back, czv, wave, reveal)
                colors[at] = Color.rgb(r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
            }
            backFace[i] = back
        }
        // Back to front.
        for (i in 0 until RINGS) order[i] = i
        for (i in 1 until RINGS) {
            val key = order[i]
            val d = depth[key] + depth[(key + 1) % RINGS]
            var j = i - 1
            while (j >= 0 && depth[order[j]] + depth[(order[j] + 1) % RINGS] < d) { order[j + 1] = order[j]; j-- }
            order[j + 1] = key
        }
    }

    private var rotY = 0f
    private var rotZ = 0f

    /** Yaw, then pitch, then roll. Returns x; y and z land in [rotY] and [rotZ]. */
    private fun rotX(x: Float, y: Float, z: Float, cyw: Float, syw: Float, cp: Float, sp: Float, cr: Float, sr: Float): Float {
        val x1 = x * cyw + z * syw
        val z1 = -x * syw + z * cyw
        val y2 = y * cp - z1 * sp
        val z2 = y * sp + z1 * cp
        rotY = x1 * sr + y2 * cr
        rotZ = z2
        return x1 * cr - y2 * sr
    }

    /** Lambert plus a tight specular and a Fresnel rim in the opposite face's color, into r/g/b. */
    private fun shade(
        f: Frame, nx: Float, ny: Float, nz: Float, lx0: Float, ly0: Float, lz0: Float,
        back: Boolean, z: Float, wave: Float, reveal: Float,
    ) {
        val ll = sqrt(lx0 * lx0 + ly0 * ly0 + lz0 * lz0)
        val lx = lx0 / ll; val ly = ly0 / ll; val lz = lz0 / ll
        val diff = (nx * lx + ny * ly + nz * lz).coerceAtLeast(0f)
        // Half vector between the light and the view direction (0, 0, -1).
        var hx = lx; var hy = ly; var hz = lz - 1f
        val hl = sqrt(hx * hx + hy * hy + hz * hz); hx /= hl; hy /= hl; hz /= hl
        // A broad sheen plus a tight glint, like lacquer.
        val nh = (nx * hx + ny * hy + nz * hz).coerceAtLeast(0f)
        val spec = nh.pow(70f) + nh.pow(9f) * 0.22f
        val fres = (1f - abs(nz)).pow(2.4f)
        val face = if (back) f.c2 else f.c1
        val other = if (back) f.c1 else f.c2
        // Far side of the loop sinks into the dark (or the paper).
        val near = (0.62f + (-z / RX) * 0.38f).coerceIn(0.25f, 1f)
        val c = f.charge
        if (f.night) {
            val lo = mix(f.c0, face, 0.08f)
            val hi = mix(f.c0, face, 0.48f)
            val light = (diff * 0.95f + 0.05f) * near
            r = Color.red(lo) + (Color.red(hi) - Color.red(lo)) * light
            g = Color.green(lo) + (Color.green(hi) - Color.green(lo)) * light
            b = Color.blue(lo) + (Color.blue(hi) - Color.blue(lo)) * light
            val sp = (spec * (0.72f + c * 0.7f) + wave * 0.35f) * near
            r += (225f + Color.red(face) * 0.12f) * sp; g += (230f + Color.green(face) * 0.1f) * sp; b += (235f) * sp
            val rim = fres * 0.7f * near
            r += Color.red(other) * rim; g += Color.green(other) * rim; b += Color.blue(other) * rim
        } else {
            val lo = mix(f.c0, face, 0.8f)
            val hi = mix(f.c0, face, 0.12f)
            val light = diff * near + (1f - near) * 0.6f
            r = Color.red(lo) + (Color.red(hi) - Color.red(lo)) * light
            g = Color.green(lo) + (Color.green(hi) - Color.green(lo)) * light
            b = Color.blue(lo) + (Color.blue(hi) - Color.blue(lo)) * light
            val sp = spec * 0.9f + wave * 0.3f
            r += (255f - r) * sp; g += (255f - g) * sp; b += (255f - b) * sp
            val rim = fres * 0.55f
            r += (Color.red(other) - r) * rim; g += (Color.green(other) - g) * rim; b += (Color.blue(other) - b) * rim
        }
        // Waking: the band surfaces from the ground color.
        val bg = f.c0
        r = Color.red(bg) + (r - Color.red(bg)) * reveal
        g = Color.green(bg) + (g - Color.green(bg)) * reveal
        b = Color.blue(bg) + (b - Color.blue(bg)) * reveal
    }

    // endregion

    // region Drawing

    private fun band(canvas: Canvas, f: Frame, reveal: Float) {
        val p = k.fill
        val s = k.stroke
        val night = f.night
        val vertices = canvas.supportsVertices()
        // The fringe widens with the swipe's speed and with each passing pulse.
        val sdx = 2.4f
        val sdy = -0.9f
        ghosts(canvas, f, reveal)
        for (n in 0 until RINGS) {
            val i = order[n]
            val j = (i + 1) % RINGS
            k.normal(p)
            p.style = Paint.Style.FILL
            p.color = Color.WHITE
            if (vertices) {
                canvas.drawVertices(Canvas.VertexMode.TRIANGLES, verts.size, verts, 0, null, 0, colors, 0,
                    indices, i * STRIPS * 6, STRIPS * 6, p)
            } else {
                for (v in 0 until STRIPS) {
                    val a = i * ROWS + v; val c = j * ROWS + v
                    k.path.reset()
                    k.path.moveTo(verts[a * 2], verts[a * 2 + 1]); k.path.lineTo(verts[a * 2 + 2], verts[a * 2 + 3])
                    k.path.lineTo(verts[c * 2 + 2], verts[c * 2 + 3]); k.path.lineTo(verts[c * 2], verts[c * 2 + 1])
                    k.path.close()
                    p.color = colors[a]
                    canvas.drawPath(k.path, p)
                }
            }
            // Edges: anti-aliased, with a chromatic split either side.
            val a0 = i * ROWS; val a1 = i * ROWS + ROWS - 1
            val b0 = j * ROWS; val b1 = j * ROWS + ROWS - 1
            edge[0] = verts[a0 * 2]; edge[1] = verts[a0 * 2 + 1]; edge[2] = verts[b0 * 2]; edge[3] = verts[b0 * 2 + 1]
            edge[4] = verts[a1 * 2]; edge[5] = verts[a1 * 2 + 1]; edge[6] = verts[b1 * 2]; edge[7] = verts[b1 * 2 + 1]
            s.strokeCap = Paint.Cap.BUTT
            k.additive(s, night)
            s.strokeWidth = 1.3f
            s.color = if (night) f.c2 else f.c2
            s.alpha = ((if (night) 150 else 120) * reveal).toInt()
            canvas.save(); canvas.translate(-sdx, -sdy); canvas.drawLines(edge, 0, 8, s); canvas.restore()
            s.color = f.c1
            canvas.save(); canvas.translate(sdx, sdy); canvas.drawLines(edge, 0, 8, s); canvas.restore()
            k.normal(s)
            s.strokeWidth = 1.6f
            s.color = if (night) mix(colors[a0], Color.WHITE, 0.45f) else mix(colors[a0], Color.BLACK, 0.35f)
            s.alpha = (230 * reveal).toInt()
            canvas.drawLines(edge, 0, 8, s)
        }
        k.reset(p)
        k.reset(s)
        s.strokeCap = Paint.Cap.ROUND
    }

    /**
     * Color-split afterimages of both edges, offset against the swipe: the band smears into
     * its two colors while it is moving. Drawn behind the band.
     */
    private fun ghosts(canvas: Canvas, f: Frame, reveal: Float) {
        val speed = f.flowSpeed
        if (speed < 40f) return
        val amount = (speed / 2500f).coerceAtMost(1f)
        val ux = -f.flowX / speed; val uy = -f.flowY / speed
        val s = k.stroke
        k.additive(s, f.night)
        s.strokeWidth = 1.6f
        for (pass in 0 until 4) {
            val d = (if (pass < 2) 10f else 22f) * amount * 2f
            s.color = if (pass % 2 == 0) f.c1 else f.c2
            s.alpha = ((if (pass < 2) 150 else 80) * amount * reveal).toInt()
            canvas.save()
            canvas.translate(ux * d * (if (pass % 2 == 0) 1f else 0.6f), uy * d * (if (pass % 2 == 0) 1f else 0.6f))
            for (side in 0..1) {
                val row = side * (ROWS - 1)
                k.path.reset()
                for (i in 0..RINGS) {
                    val at = (i % RINGS) * ROWS + row
                    if (i == 0) k.path.moveTo(verts[at * 2], verts[at * 2 + 1]) else k.path.lineTo(verts[at * 2], verts[at * 2 + 1])
                }
                canvas.drawPath(k.path, s)
            }
            canvas.restore()
        }
        k.reset(s)
    }

    /** A soft diagonal shaft of light behind the band, drifting slowly. */
    private fun streak(canvas: Canvas, f: Frame, reveal: Float) {
        val mode = if (f.night) RenderKit.Mode.ADD else RenderKit.Mode.INK
        val drift = sin(f.t * 0.06f) * 60f * f.amount
        canvas.save()
        canvas.translate(520f + drift + f.shiftX(0.25f), f.h * 0.55f + f.shiftY(0.25f))
        canvas.rotate(-38f)
        k.glow(canvas, k.glowA, -60f, -40f, 1100f, 120f, ((if (f.night) 40 else 34) * reveal).toInt(), mode)
        k.glow(canvas, k.glowB, 40f, 30f, 900f, 42f, ((if (f.night) 52 else 46) * reveal).toInt(), mode)
        k.glow(canvas, k.glowWhite, 120f, 34f, 600f, 7f, ((if (f.night) 30 else 0) * reveal).toInt(), mode)
        canvas.restore()
    }

    /** Floating dust with depth of field: far motes are fine points, near ones soft discs. */
    private fun dust(canvas: Canvas, f: Frame, reveal: Float, near: Boolean) {
        moteCount.fill(0)
        val count = (MOTES * (0.4f + f.detail * 0.6f)).toInt()
        for (i in 0 until count) {
            val seed = i * 6151 + 29
            val z = fraction(seed)
            if ((z > 0.82f) != near) continue
            val d = 0.3f + z * 1.6f
            val x0 = fraction(seed + 1) * 1100f - 50f
            val y0 = fraction(seed + 2) * (f.h + 200f)
            var x = x0 + sin(f.t * 0.13f + seed) * 30f * f.amount + f.shiftX(d) + f.flowX * 0.02f * d
            var y = (y0 - f.t * (2.5f + z * 4.5f) + f.shiftY(d) + f.flowY * 0.02f * d) % (f.h + 200f)
            if (y < 0f) y += f.h + 200f
            y -= 100f
            if (f.pulses > 0) {
                val w = f.wave(x, y, 100f)
                x += w * 20f; y -= w * 20f
            }
            val bucket = if (near) 2 else if (fraction(seed + 3) < 0.5f) 0 else 1
            val at = moteCount[bucket] * 2
            motes[bucket][at] = x; motes[bucket][at + 1] = y
            moteCount[bucket]++
        }
        val p = k.dots
        k.additive(p, f.night)
        for (bkt in 0 until 3) {
            if (moteCount[bkt] == 0) continue
            p.color = when (bkt) { 0 -> f.c1; 1 -> f.c2; else -> if (f.night) mix(f.c1, Color.WHITE, 0.5f) else f.c1 }
            p.strokeWidth = if (bkt == 2) 15f else 2.2f
            p.alpha = ((if (bkt == 2) (if (f.night) 22 else 16) else (if (f.night) 120 else 90)) * reveal).toInt()
            canvas.drawPoints(motes[bkt], 0, moteCount[bkt] * 2, p)
        }
        k.reset(p)
    }

    private fun touch(canvas: Canvas, f: Frame) {
        val night = f.night
        val mode = if (night) RenderKit.Mode.ADD else RenderKit.Mode.INK
        if (f.drag > 0.01f) {
            val c = f.charge
            k.glow(canvas, k.glowWhite, f.dragX, f.dragY, 22f + c * 40f, 22f + c * 40f, ((90 + c * 150) * f.drag).toInt(), mode)
            k.glow(canvas, k.glowA, f.dragX, f.dragY, 120f + c * 90f, 120f + c * 90f, ((24 + c * 50) * f.drag).toInt(), mode)
        }
        val s = k.stroke
        if (f.trail > 1) {
            // A chromatic afterimage of the finger's sweep.
            k.additive(s, night)
            for (pass in 0 until 2) {
                s.color = if (pass == 0) f.c1 else f.c2
                s.strokeWidth = 3f
                k.path.reset()
                for (j in 0 until f.trail) {
                    val x = f.trailX[j] + (if (pass == 0) 4f else -4f)
                    val y = f.trailY[j] + (if (pass == 0) -2f else 2f)
                    if (j == 0) k.path.moveTo(x, y) else k.path.lineTo(x, y)
                }
                s.alpha = ((if (night) 110 else 120) * f.trailA[f.trail - 1]).toInt()
                canvas.drawPath(k.path, s)
            }
        }
        for (i in 0 until f.pulses) {
            val a = f.pulseA[i].coerceAtMost(1.5f)
            if (a < 0.02f) continue
            k.additive(s, night)
            s.strokeWidth = 2f + a * 2f
            s.color = f.c1; s.alpha = (a * 70).toInt().coerceAtMost(255); canvas.drawCircle(f.pulseX[i] + 4f, f.pulseY[i], f.pulseR[i], s)
            s.color = f.c2; canvas.drawCircle(f.pulseX[i] - 4f, f.pulseY[i], f.pulseR[i], s)
        }
        k.reset(s)
    }

    // endregion

    private companion object {
        const val RINGS = 150
        const val STRIPS = 4
        const val ROWS = STRIPS + 1
        const val MOTES = 160
        const val RX = 300f
        const val RY = 600f
        const val RZ = 170f
        const val CURL = 26f
        const val FOCAL = 1700f
        const val DIST = 1700f
        const val TAU = (2 * PI).toFloat()
    }
}
