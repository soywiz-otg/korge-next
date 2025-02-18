package com.soywiz.korge.view

import com.soywiz.korge.ui.*
import com.soywiz.korim.color.*
import com.soywiz.korim.paint.Paint
import com.soywiz.korma.geom.shape.*
import com.soywiz.korma.geom.vector.*

/**
 * Creates a [Circle] of [radius] and [fill].
 * The [autoScaling] determines if the underlying texture will be updated when the hierarchy is scaled.
 * The [callback] allows to configure the [Circle] instance.
 */
inline fun Container.circle(
    radius: Double = 16.0,
    fill: Paint = Colors.WHITE,
    stroke: Paint = Colors.WHITE,
    strokeThickness: Double = 0.0,
    autoScaling: Boolean = true,
    callback: @ViewDslMarker Circle.() -> Unit = {}
): Circle = Circle(radius, fill, stroke, strokeThickness, autoScaling).addTo(this, callback)

/**
 * A [Graphics] class that automatically keeps a circle shape with [radius] and [color].
 * The [autoScaling] property determines if the underlying texture will be updated when the hierarchy is scaled.
 */
open class Circle(
    radius: Double = 16.0,
    fill: Paint = Colors.WHITE,
    stroke: Paint = Colors.WHITE,
    strokeThickness: Double = 0.0,
    autoScaling: Boolean = true,
) : ShapeView(shape = VectorPath(), fill = fill, stroke = stroke, strokeThickness = strokeThickness, autoScaling = autoScaling) {
    /** Radius of the circle */
    var radius: Double by uiObservable(radius) { updateGraphics() }
    /** Color of the circle. Internally it uses the [colorMul] property */
    var color: RGBA
        get() = colorMul
        set(value) { colorMul = value }

    init {
        this.color = color
        updateGraphics()
    }

    private fun updateGraphics() {
        val halfStroke = this@Circle.strokeThickness / 2
        val radius = this.radius
        hitShape2d = Shape2d.Circle(radius, radius, radius)
        //println("radius=$radius, halfStroke=$halfStroke")
        updateShape {
            clear()
            circle(radius, radius, radius)
            //circle(radius + halfStroke, radius + halfStroke, radius)
            //println(toSvgString())
        }
    }
}
