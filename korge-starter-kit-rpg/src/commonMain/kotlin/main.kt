import com.soywiz.klock.*
import com.soywiz.korev.*
import com.soywiz.korge.*
import com.soywiz.korge.component.docking.*
import com.soywiz.korge.scene.*
import com.soywiz.korge.tiled.*
import com.soywiz.korge.tween.*
import com.soywiz.korge.view.*
import com.soywiz.korge.view.animation.*
import com.soywiz.korge.view.camera.*
import com.soywiz.korge.view.filter.*
import com.soywiz.korge.view.tween.*
import com.soywiz.korim.atlas.*
import com.soywiz.korim.color.*
import com.soywiz.korim.format.*
import com.soywiz.korio.file.std.*
import com.soywiz.korma.geom.*
import com.soywiz.korma.geom.shape.*
import com.soywiz.korma.interpolation.*

suspend fun main() = Korge(width = 512, height = 512, bgcolor = Colors["#2b2b2b"]) {
    injector.mapPrototype { RpgIngameScene() }

    val rootSceneContainer = sceneContainer()

    rootSceneContainer.changeTo<RpgIngameScene>(
        transition = MaskTransition(transition = TransitionFilter.Transition.CIRCULAR, reversed = false, smooth = true),
        time = 0.5.seconds
    )
}

class RpgIngameScene : Scene() {
    val atlas = MutableAtlasUnit(2048, 2048)
    lateinit var tilemap: TiledMap
    lateinit var characters: ImageDataContainer

    override suspend fun Container.sceneInit() {

        val sw = Stopwatch().start()

        tilemap = resourcesVfs["BasicTilemap/untitled.tmx"].readTiledMap(atlas = atlas)
        characters = resourcesVfs["vampire.ase"].readImageDataContainer(ASE, atlas = atlas)

        println("loaded resources in ${sw.elapsed}")
    }

    override suspend fun Container.sceneMain() {
        container {
            scale(2.0)

            lateinit var character: ImageDataView
            lateinit var tiledMapView: TiledMapView

            val cameraContainer = cameraContainer(
                256.0, 256.0, clip = true,
                block = {
                    clampToBounds = true
                }
            ) {
                tiledMapView = tiledMapView(tilemap, smoothing = false, showShapes = false)

                println("tiledMapView[\"start\"]=${tiledMapView["start"].firstOrNull}")
                val npcs = tiledMapView.tiledMap.data.getObjectByType("npc")
                for (obj in tiledMapView.tiledMap.data.objectLayers.objects) {
                    println("- obj = $obj")
                }
                println("NPCS=$npcs")
                println(tiledMapView.firstDescendantWith { it.getPropString("type") == "start" })
                val startPos = tiledMapView["start"].firstOrNull?.pos ?: Point(50, 50)
                val charactersLayer = tiledMapView["characters"].first as Container

                println("charactersLayer=$charactersLayer")

                charactersLayer.keepChildrenSortedByY()

                for (obj in tiledMapView.tiledMap.data.getObjectByType("npc")) {
                    val npc = charactersLayer.imageDataView(
                        characters[obj.str("skin")],
                        "down",
                        playing = false,
                        smoothing = false
                    ) {
                        hitShape2d = Shape2d.Rectangle.fromBounds(-8.0, -3.0, +8.0, +3.0)
                        xy(obj.x, obj.y)
                    }
                }
                character =
                    charactersLayer.imageDataView(characters["vampire"], "right", playing = false, smoothing = false) {
                        hitShape2d = Shape2d.Rectangle.fromBounds(-8.0, -3.0, +8.0, +3.0)
                        xy(startPos)
                    }
            }

            cameraContainer.cameraViewportBounds.copyFrom(tiledMapView.getLocalBoundsOptimized())

            stage!!.controlWithKeyboard(character, listOf(tiledMapView))

            cameraContainer.follow(character, setImmediately = true)

            //cameraContainer.tweenCamera(cameraContainer.getCameraRect(Rectangle(200, 200, 100, 100)))
        }
    }
}

fun Stage.controlWithKeyboard(
	char: ImageDataView,
	//collider: HitTestable,
    collider: List<View>,
	up: Key = Key.UP,
	right: Key = Key.RIGHT,
	down: Key = Key.DOWN,
	left: Key = Key.LEFT,
) {
	addUpdater { dt ->
		val speed = 2.0 * (dt / 16.0.milliseconds)
		val dx = if (keys[left]) -1.0 else if (keys[right]) +1.0 else 0.0
		val dy = if (keys[up]) -1.0 else if (keys[down]) +1.0 else 0.0
		if (dx != 0.0 || dy != 0.0) {
			val dpos = Point(dx, dy).normalized * speed
			//char.moveWithHitTestable(collider.first(), dpos.x, dpos.y)
            char.moveWithCollisions(collider, dpos.x, dpos.y)
		}
		char.animation = when {
			dx < 0.0 -> "left"
			dx > 0.0 -> "right"
			dy < 0.0 -> "up"
			dy > 0.0 -> "down"
			else -> char.animation
		}
		if (dx != 0.0 || dy != 0.0) {
			char.play()
		} else {
			char.stop()
			char.rewind()
		}
	}
}
