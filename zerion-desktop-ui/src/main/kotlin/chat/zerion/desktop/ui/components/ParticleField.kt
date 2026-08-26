package chat.zerion.desktop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

import java.util.Random

/**
 * Animated particle-field background, a faithful port of the Android login
 * screen's ParticleFieldView: 65 drifting, twinkling particles in the Zerion
 * blues on a near-black ground, joined by fading lines when they pass close.
 * This is the same "blinking" background used on the phone app and the website.
 */
private const val BACKGROUND = 0xFF0B0E15
private val PALETTE = longArrayOf(0xFF00E1FF, 0xFF00B8FF, 0xFF0066FF)
private const val PARTICLE_COUNT = 65
private const val CONNECTION_DISTANCE_DP = 150f
private const val MIN_RADIUS_DP = 1.5f
private const val MAX_RADIUS_DP = 3.5f
private const val MIN_SPEED_DP = 0.15f
private const val MAX_SPEED_DP = 0.6f
private const val MIN_ALPHA = 0.25f
private const val MAX_ALPHA = 0.9f
private const val LINE_MAX_ALPHA = 0.3f

@Composable
fun ParticleField(modifier: Modifier = Modifier) {
	BoxWithConstraints(modifier.background(Color(BACKGROUND))) {
		val density = LocalDensity.current.density
		val w = with(LocalDensity.current) { maxWidth.toPx() }
		val h = with(LocalDensity.current) { maxHeight.toPx() }
		if (w < 1f || h < 1f) return@BoxWithConstraints

		val field = remember(w, h) { ParticleState(w, h, density) }
		var frame by remember { mutableStateOf(0L) }
		LaunchedFrames { dt -> field.update(dt); frame = frame + 1 }

		Canvas(Modifier.fillMaxSize()) {
			frame
			field.draw(this)
		}
	}
}

@Composable
private fun LaunchedFrames(onFrame: (Float) -> Unit) {
	androidx.compose.runtime.LaunchedEffect(Unit) {
		var last = 0L
		while (true) {
			withFrameNanos { now ->
				if (last != 0L) {
					val dt = ((now - last) / 1_000_000_000.0).toFloat()
					onFrame(minOf(dt, 0.05f))
				}
				last = now
			}
		}
	}
}

private class ParticleState(
		private val w: Float,
		private val h: Float,
		density: Float,
) {
	private val n = PARTICLE_COUNT
	private val px = FloatArray(n)
	private val py = FloatArray(n)
	private val vx = FloatArray(n)
	private val vy = FloatArray(n)
	private val radius = FloatArray(n)
	private val baseAlpha = FloatArray(n)
	private val phase = FloatArray(n)
	private val colors = Array(n) { Color.White }

	private val connDist = CONNECTION_DISTANCE_DP * density
	private val connDistSq = connDist * connDist

	init {
		val rnd = Random()
		for (i in 0 until n) {
			px[i] = rnd.nextFloat() * w
			py[i] = rnd.nextFloat() * h
			val angle = rnd.nextFloat() * 2f * Math.PI.toFloat()
			val speed = (MIN_SPEED_DP + rnd.nextFloat() *
					(MAX_SPEED_DP - MIN_SPEED_DP)) * density * 60f
			vx[i] = (Math.cos(angle.toDouble()).toFloat()) * speed
			vy[i] = (Math.sin(angle.toDouble()).toFloat()) * speed
			radius[i] = (MIN_RADIUS_DP + rnd.nextFloat() *
					(MAX_RADIUS_DP - MIN_RADIUS_DP)) * density
			baseAlpha[i] = MIN_ALPHA + rnd.nextFloat() * (MAX_ALPHA - MIN_ALPHA)
			phase[i] = rnd.nextFloat() * 2f * Math.PI.toFloat()
			colors[i] = Color(PALETTE[rnd.nextInt(PALETTE.size)])
		}
	}

	fun update(dt: Float) {
		for (i in 0 until n) {
			px[i] += vx[i] * dt
			py[i] += vy[i] * dt
			phase[i] += dt * 1.8f
			if (px[i] < -connDist) px[i] = w + connDist
			if (px[i] > w + connDist) px[i] = -connDist
			if (py[i] < -connDist) py[i] = h + connDist
			if (py[i] > h + connDist) py[i] = -connDist
		}
	}

	fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope) {
		for (i in 0 until n) {
			for (j in i + 1 until n) {
				val dx = px[i] - px[j]
				val dy = py[i] - py[j]
				val d2 = dx * dx + dy * dy
				if (d2 < connDistSq) {
					val dist = Math.sqrt(d2.toDouble()).toFloat()
					val a = (1f - dist / connDist) * LINE_MAX_ALPHA
					scope.drawLine(
							color = colors[i].copy(alpha = a),
							start = Offset(px[i], py[i]),
							end = Offset(px[j], py[j]),
							strokeWidth = 1f)
				}
			}
		}
		for (i in 0 until n) {
			val twinkle = 0.6f + 0.4f *
					(0.5f + 0.5f * Math.sin(phase[i].toDouble()).toFloat())
			val a = (baseAlpha[i] * twinkle).coerceIn(0f, 1f)
			scope.drawCircle(
					color = colors[i].copy(alpha = a * 0.28f),
					radius = radius[i] * 2.4f,
					center = Offset(px[i], py[i]))
			scope.drawCircle(
					color = colors[i].copy(alpha = a),
					radius = radius[i],
					center = Offset(px[i], py[i]))
		}
	}
}
