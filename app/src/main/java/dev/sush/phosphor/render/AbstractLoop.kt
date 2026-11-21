package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import dev.sush.phosphor.render.RenderKit.Companion.mix
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A single beveled ribbon follows a closed trefoil. Its three crossings alternate over and under. */
internal class AbstractLoop(private val k: RenderKit) {
    private data class Vec(val x: Float, val y: Float, val z: Float) {
        fun unit(): Vec {
            val length = sqrt(x * x + y * y + z * z)
            return Vec(x / length, y / length, z / length)
        }
        fun cross(v: Vec) = Vec(y * v.z - z * v.y, z * v.x - x * v.z, x * v.y - y * v.x)
    }

    private data class Section(val center: Vec, val across: Vec, val up: Vec, val dye: Float)
    private val sections = Array(RINGS) { i ->
        val u = i * TURN / RINGS
        val center = Vec(109f * (sin(u) + 2f * sin(2f * u)),
            141f * (cos(u) - 2f * cos(2f * u)), -155f * sin(3f * u))
        val tangent = Vec(109f * (cos(u) + 4f * cos(2f * u)),
            141f * (-sin(u) + 4f * sin(2f * u)), -465f * cos(3f * u)).unit()
        val outward = Vec(-tangent.y, tangent.x, 0f).unit()
        val upright = tangent.cross(outward)
        val twist = 0.40f + 0.81f * sin(u + 0.48f)
        val c = cos(twist); val s = sin(twist)
        val across = Vec(outward.x * c + upright.x * s, outward.y * c + upright.y * s, upright.z * s)
        Section(center, across, tangent.cross(across), 0.5f + 0.47f * cos(u - 1.1f))
    }
    // Rounded rectangular section. Closely spaced vertices at the shoulders produce a narrow polished bevel.
    private val sectionX = FloatArray(SIDES) { signedPower(cos(it * TURN / SIDES), 0.64f) }
    private val sectionZ = FloatArray(SIDES) { signedPower(sin(it * TURN / SIDES), 0.61f) }
    private val verts = FloatArray(RINGS * SIDES * 2)
    private val colors = IntArray(RINGS * SIDES)
    private val indices = ShortArray(RINGS * SIDES * 6)
    private val depth = FloatArray(RINGS)
    private val order = IntArray(RINGS)
    private val normalX = FloatArray(SIDES)
    private val normalZ = FloatArray(SIDES)

    fun draw(canvas: Canvas, f: Frame) {
        val cx = 505f + f.shiftX(0.95f)
        val cy = f.h * 0.53f + f.shiftY(0.95f)
        val size = minOf(1.18f, f.h / 1100f)
        k.glow(canvas, k.glowA, cx - 140f, cy - 35f * size, 590f * size, 595f * size, 34)
        k.glow(canvas, k.glowB, cx + 130f, cy + 126f * size, 420f * size, 420f * size, 22)

        val nativeMesh = canvas.supportsVertices()
        val step = if (!nativeMesh || f.detail < 0.64f) 2 else 1
        val rings = RINGS / step
        build(f, cx, cy, size, step, rings)
        val count = visibleTriangles(rings)
        val paint = k.fill
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        k.normal(paint)
        if (nativeMesh) {
            canvas.drawVertices(Canvas.VertexMode.TRIANGLES, rings * SIDES * 2, verts, 0,
                null, 0, colors, 0, indices, 0, count, paint)
        } else {
            for (at in 0 until count step 6) {
                val a = indices[at].toInt(); val b = indices[at + 1].toInt()
                val c = indices[at + 2].toInt(); val d = indices[at + 5].toInt()
                k.path.rewind()
                k.path.moveTo(verts[a * 2], verts[a * 2 + 1])
                k.path.lineTo(verts[b * 2], verts[b * 2 + 1])
                k.path.lineTo(verts[c * 2], verts[c * 2 + 1])
                k.path.lineTo(verts[d * 2], verts[d * 2 + 1])
                k.path.close()
                paint.color = colors[a]
                canvas.drawPath(k.path, paint)
            }
        }
        k.reset(paint)
    }

    private fun build(f: Frame, cx: Float, cy: Float, size: Float, step: Int, rings: Int) {
        val yaw = -0.12f + sin(f.t * 0.19f) * 0.31f * f.amount
        val pitch = -0.19f + sin(f.t * 0.13f + 0.5f) * 0.19f * f.amount
        val roll = -0.23f + sin(f.t * 0.09f) * 0.08f * f.amount
        val sy = sin(yaw); val cp = cos(pitch); val cr = cos(roll)
        val cyaw = cos(yaw); val sp = sin(pitch); val sr = sin(roll)
        // Rows of the object-to-camera rotation; the camera is on positive Z.
        val xx = cr * cyaw - sr * sp * sy; val xy = -sr * cp; val xz = cr * sy + sr * sp * cyaw
        val yx = sr * cyaw + cr * sp * sy; val yy = cr * cp; val yz = sr * sy - cr * sp * cyaw
        val zx = -sy * cp; val zy = sp; val zz = cp * cyaw
        fun x(v: Vec) = xx * v.x + xy * v.y + xz * v.z
        fun y(v: Vec) = yx * v.x + yy * v.y + yz * v.z
        fun z(v: Vec) = zx * v.x + zy * v.y + zz * v.z

        val width = 57f + 48f * f.composition
        val thickness = 10f
        for (j in 0 until SIDES) {
            val prev = (j + SIDES - 1) % SIDES; val next = (j + 1) % SIDES
            val nx = (sectionZ[next] - sectionZ[prev]) * thickness
            val nz = (sectionX[prev] - sectionX[next]) * width
            val inv = 1f / sqrt(nx * nx + nz * nz)
            normalX[j] = nx * inv
            normalZ[j] = nz * inv
        }
        val lightX = -0.38f + 0.17f * sin(f.t * 0.28f) * f.amount
        for (i in 0 until rings) {
            val section = sections[i * step]
            val px = x(section.center); val py = y(section.center); val pz = z(section.center)
            val ax = x(section.across); val ay = y(section.across); val az = z(section.across)
            val ux = x(section.up); val uy = y(section.up); val uz = z(section.up)
            depth[i] = pz
            val base = mix(f.c1, f.c2, section.dye)
            val highlight = mix(base, Color.WHITE, 0.76f)
            val recess = (0.74f + pz * 0.00145f).coerceIn(0.44f, 1f)
            for (j in 0 until SIDES) {
                val at = i * SIDES + j
                val a = sectionX[j] * width; val b = sectionZ[j] * thickness
                val perspective = size * 1600f / (1600f - pz - az * a - uz * b)
                verts[at * 2] = cx + (px + ax * a + ux * b) * perspective
                verts[at * 2 + 1] = cy + (py + ay * a + uy * b) * perspective
                val nx = ax * normalX[j] + ux * normalZ[j]
                val ny = ay * normalX[j] + uy * normalZ[j]
                val nz = az * normalX[j] + uz * normalZ[j]
                val diffuse = (nx * lightX - ny * 0.63f + nz * 0.64f).coerceIn(0f, 1f)
                val facing = nz.coerceIn(0f, 1f)
                // Reflection of a long studio strip; the opposite side catches a narrow pink return.
                val reflection = 2f * facing * (nx * 0.88f + ny * 0.47f)
                val strip = (1f - abs(reflection - lightX) * 2.7f).coerceIn(0f, 1f)
                val edgelight = (1f - facing) * (1f - facing)
                val body = mix(f.c0, base, recess * (0.10f + 0.55f * diffuse + 0.16f * edgelight))
                val glint = (strip * strip * (0.16f + 0.28f * diffuse) + 0.13f * edgelight) * recess
                colors[at] = mix(body, highlight, glint)
            }
        }
    }

    private fun visibleTriangles(rings: Int): Int {
        // Sort sections by midpoint depth. Occluded portions of a crossing are naturally overpainted.
        for (i in 0 until rings) {
            val z = depth[i] + depth[(i + 1) % rings]
            var j = i
            while (j > 0) {
                val earlier = order[j - 1]
                if (depth[earlier] + depth[(earlier + 1) % rings] <= z) break
                order[j] = earlier
                j--
            }
            order[j] = i
        }
        var n = 0
        for (i in 0 until rings) {
            val ring = order[i]
            val nextRing = (ring + 1) % rings
            for (j in 0 until SIDES) {
                val next = (j + 1) % SIDES
                val a = ring * SIDES + j; val b = ring * SIDES + next
                val c = nextRing * SIDES + next; val d = nextRing * SIDES + j
                val abX = verts[b * 2] - verts[a * 2]; val abY = verts[b * 2 + 1] - verts[a * 2 + 1]
                val acX = verts[c * 2] - verts[a * 2]; val acY = verts[c * 2 + 1] - verts[a * 2 + 1]
                if (abX * acY - abY * acX <= 0f) continue
                indices[n++] = a.toShort(); indices[n++] = b.toShort(); indices[n++] = c.toShort()
                indices[n++] = a.toShort(); indices[n++] = c.toShort(); indices[n++] = d.toShort()
            }
        }
        return n
    }

    private fun signedPower(x: Float, exponent: Float): Float = if (x < 0f) -(-x).pow(exponent) else x.pow(exponent)

    private companion object {
        const val RINGS = 144
        const val SIDES = 24
        const val TURN = (2 * PI).toFloat()
    }
}
