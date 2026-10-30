package com.zozi.feels_likeweather.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

internal data class Particle(
    val angle: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val confetti: Boolean,
    val spin: Float,
)

internal class Burst(val id: Long, val origin: Offset, val particles: List<Particle>)

/** 버튼 위에서 터지는 파티클 효과를 관리한다. [ParticleLayer] 와 함께 쓴다. */
class ParticleBurstState {
    internal val bursts = mutableStateListOf<Burst>()
    private var nextId = 0L

    fun burst(origin: Offset, colors: List<Color>, count: Int = 22, random: Random = Random.Default) {
        val particles = List(count) {
            // 위쪽으로 부채꼴 형태로 퍼지도록 각도를 제한한다 (-160° ~ -20°, 화면 좌표계).
            val angle = Math.toRadians(-160.0 + random.nextDouble() * 140.0).toFloat()
            Particle(
                angle = angle,
                speed = 0.55f + random.nextFloat() * 0.45f,
                size = 0.6f + random.nextFloat() * 0.6f,
                color = colors[random.nextInt(colors.size)],
                confetti = random.nextBoolean(),
                spin = random.nextFloat() * 720f - 360f,
            )
        }
        bursts += Burst(nextId++, origin, particles)
    }

    internal fun remove(id: Long) {
        bursts.removeAll { it.id == id }
    }
}

@Composable
fun rememberParticleBurstState(): ParticleBurstState = remember { ParticleBurstState() }

/** 부모 크기를 채우는 파티클 레이어. 콘텐츠 위에 겹쳐 배치한다. */
@Composable
fun ParticleLayer(state: ParticleBurstState, modifier: Modifier = Modifier) {
    val dp = LocalDensity.current.density
    val distance = 90f * dp
    val gravity = 70f * dp
    val dot = 4f * dp
    for (burst in state.bursts.toList()) {
        key(burst.id) {
            val progress = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                progress.animateTo(1f, tween(850, easing = LinearOutSlowInEasing))
                state.remove(burst.id)
            }
            Canvas(modifier.fillMaxSize()) {
                val p = progress.value
                val alpha = (1f - p * p).coerceIn(0f, 1f)
                burst.particles.forEach { particle ->
                    val r = distance * particle.speed * p
                    val center = Offset(
                        burst.origin.x + cos(particle.angle) * r,
                        burst.origin.y + sin(particle.angle) * r + gravity * p * p,
                    )
                    val s = dot * particle.size * (1f - 0.3f * p)
                    if (particle.confetti) {
                        rotate(particle.spin * p, pivot = center) {
                            drawRoundRect(
                                color = particle.color.copy(alpha = alpha),
                                topLeft = center - Offset(s, s / 2),
                                size = Size(s * 2, s),
                                cornerRadius = CornerRadius(s / 3),
                            )
                        }
                    } else {
                        drawCircle(particle.color.copy(alpha = alpha), radius = s, center = center)
                    }
                }
            }
        }
    }
}
