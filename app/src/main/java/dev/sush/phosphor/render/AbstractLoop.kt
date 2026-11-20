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
 * A slow change of orientation rolls the highlights along the band.
 */
internal class AbstractLoop(private val k: RenderKit) {
    private val verts = FloatArray(RINGS * ROWS * 2)
    private val colors = IntArray(RINGS * ROWS)
    private val indices = ShortArray(RINGS * STRIPS * 6)
    private val depth = FloatArray(RINGS)
    private val order = IntArray(RINGS)
    private val edge = FloatArray(8)
    private val motes = Array(3) { FloatArray(MOTES * 2) }
    private val moteCount = IntArray(3)

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

        val mode = k.modeFor(f)
        k.glow(canvas, k.glowA, 255f + f.shiftX(0.3f), h * 0.46f, 720f, h * 0.33f, if (night) 70 else 45, mode)
        k.glow(canvas, k.glowB, 770f + f.shiftX(0.3f), h * 0.67f, 690f, h * 0.27f, if (night) 49 else 40, mode)
        streak(canvas, f, reveal)
        dust(canvas, f, reveal, false)
        build(f, reveal)
        band(canvas, f, reveal)
        dust(canvas, f, reveal, true)
    }

    // region Geometry and shading

    private fun build(f: Frame, reveal: Float) {
        val h = f.h
        val t = f.t
        val cx = 474f + f.shiftX(0.8f)
        val cy = h * 0.54f + f.shiftY(0.8f)
        // Orientation: a slow yaw sway plus the oscillating pitch.
        val yaw = 0.68f + sin(t * 0.19f) * 0.32f * f.amount
        val pitch = -0.5f + sin(t * 0.13f) * 0.13f * f.amount
        val roll = -0.57f + sin(t * 0.053f) * 0.07f * f.amount
        val cyw = cos(yaw); val syw = sin(yaw)
        val cp = cos(pitch); val sp = sin(pitch)
        val cr = cos(roll); val sr = sin(roll)

        // Light travels across the upper face.
        val lx = -0.5f + sin(t * 0.33f) * 0.12f * f.amount
        val ly = -0.62f; val lz = -0.6f
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
            // Two full twists around the loop, drifting, with the moving highlight.
            val tau = 2f * u + t * 0.12f + sin(t * 0.29f) * 0.4f * f.amount * (0.5f + 0.5f * cos(u - 1.2f))
            val ct = cos(tau); val st = sin(tau)
            val wx = nx * ct + bx * st; val wy = ny * ct + by * st; val wz = nz * ct + bz * st
            val sx = bx * ct - nx * st; val sy = by * ct - ny * st; val sz = bz * ct - nz * st
            var half = (128f + 52f * sin(u * 3f + 1f + t * 0.11f)) * (1f + f.composition)

            // Ring center in view space, for depth sorting.
            rotX(px, py, pz, cyw, syw, cp, sp, cr, sr)
            val czv = rotZ
            depth[i] = czv
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
                val s = FOCAL * 1.22f / (vz + DIST)
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

                shade(f, qx, qy, qz, lx, ly, lz, back, czv, reveal)
                colors[at] = Color.rgb(r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
            }
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
        back: Boolean, z: Float, reveal: Float,
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
        if (f.night) {
            val lo = mix(f.c0, face, 0.08f)
            val hi = mix(f.c0, face, 0.43f)
            val light = (diff * 0.95f + 0.05f) * near
            r = Color.red(lo) + (Color.red(hi) - Color.red(lo)) * light
            g = Color.green(lo) + (Color.green(hi) - Color.green(lo)) * light
            b = Color.blue(lo) + (Color.blue(hi) - Color.blue(lo)) * light
            val sp = spec * 0.56f * near
            r += (225f + Color.red(face) * 0.12f) * sp; g += (230f + Color.green(face) * 0.1f) * sp; b += (235f) * sp
            val rim = fres * 0.50f * near
            r += Color.red(other) * rim; g += Color.green(other) * rim; b += Color.blue(other) * rim
        } else {
            val lo = mix(f.c0, face, 0.8f)
            val hi = mix(f.c0, face, 0.12f)
            val light = diff * near + (1f - near) * 0.6f
            r = Color.red(lo) + (Color.red(hi) - Color.red(lo)) * light
            g = Color.green(lo) + (Color.green(hi) - Color.green(lo)) * light
            b = Color.blue(lo) + (Color.blue(hi) - Color.blue(lo)) * light
            val sp = spec * 0.9f
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
        // Offset the two spectral edges.
        val sdx = 2.4f
        val sdy = -0.9f
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

    private fun streak(canvas: Canvas, f: Frame, reveal: Float) {
        val mode = if (f.night) RenderKit.Mode.ADD else RenderKit.Mode.INK
        val drift = sin(f.t * 0.06f) * 60f * f.amount
        canvas.save()
        canvas.translate(520f + drift + f.shiftX(0.25f), f.h * 0.55f + f.shiftY(0.25f))
        canvas.rotate(-38f)
        k.glow(canvas, k.glowA, -60f, -40f, 1100f, 198f, ((if (f.night) 58 else 39) * reveal).toInt(), mode)
        k.glow(canvas, k.glowB, 40f, 30f, 905f, 93f, ((if (f.night) 52 else 46) * reveal).toInt(), mode)
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
            var x = x0 + sin(f.t * 0.13f + seed) * 30f * f.amount + f.shiftX(d)
            var y = (y0 - f.t * (2.5f + z * 4.5f) + f.shiftY(d)) % (f.h + 200f)
            if (y < 0f) y += f.h + 200f
            y -= 100f

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
