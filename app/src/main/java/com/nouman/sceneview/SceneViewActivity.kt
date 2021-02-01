package com.nouman.sceneview

import android.content.DialogInterface
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.sceneform.HitTestResult
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*
import com.google.ar.sceneform.ux.FootprintSelectionVisualizer
import com.google.ar.sceneform.ux.TransformationSystem
import com.nouman.sceneview.nodes.DragTransformableNode
import kotlinx.android.synthetic.main.activity_scene_view.*
import java.lang.Exception
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

class SceneViewActivity : AppCompatActivity() {
    var localModel = "arrow.glb"

    private var videoRenderable: ModelRenderable? = null
    private lateinit var texture: ExternalTexture

    // The color to filter out of the video.
    private val CHROMA_KEY_COLOR = Color(0.1843f, 1.0f, 0.098f)

    // just for testing playing a canned video
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scene_view)

        //load local model
        renderLocalObject()
    }

    private fun renderLocalObject() {

        skuProgressBar.setVisibility(View.VISIBLE)

        texture = ExternalTexture()
        val mediaPlayer = MediaPlayer.create(this, R.raw.lion_chroma).apply {
            setSurface(texture.surface)
            isLooping = true
        }
        this.mediaPlayer = mediaPlayer

        ModelRenderable.builder()
            .setSource(this, Uri.parse(localModel))
            .setIsFilamentGltf(true)
            .setRegistryId(localModel)
            .build()
            .thenAccept { modelRenderable: ModelRenderable ->
                skuProgressBar.setVisibility(View.GONE)
                addNodeToScene(modelRenderable)
            }
            .exceptionally { throwable: Throwable? ->
                var message: String?
                message = if (throwable is CompletionException) {
                    skuProgressBar.setVisibility(View.GONE)
                    "Internet is not working"
                } else {
                    skuProgressBar.setVisibility(View.GONE)
                    "Can't load Model"
                }
                val mainHandler = Handler(Looper.getMainLooper())
                val finalMessage: String = message
                val myRunnable = Runnable {
                    AlertDialog.Builder(this)
                        .setTitle("Error")
                        .setMessage(finalMessage + "")
                        .setPositiveButton("Retry") { dialogInterface: DialogInterface, _: Int ->
                            renderLocalObject()
                            dialogInterface.dismiss()
                        }
                        .setNegativeButton("Cancel") { dialogInterface, _ -> dialogInterface.dismiss() }
                        .show()
                }
                mainHandler.post(myRunnable)
                null
            }

        Material.builder()
            .setSource(
                this,
                com.google.ar.sceneform.ux.R.raw.sceneform_camera_material
            )
            .build()
            .thenAccept { material ->
                val vertices: ArrayList<Vertex> = ArrayList()
                // bottom left
                vertices.add(
                    Vertex.builder()
                        .setPosition(Vector3(-1.0f, -1.0f, 1.0f))
                        .setNormal(Vector3(0.0f, 0.0f, 1.0f))
                        .setUvCoordinate(Vertex.UvCoordinate(0.0f, 0.0f))
                        .build()
                )
                // bottom right
                vertices.add(
                    Vertex.builder()
                        .setPosition(Vector3(1.0f, -1.0f, 1.0f))
                        .setNormal(Vector3(0.0f, 0.0f, 1.0f))
                        .setUvCoordinate(Vertex.UvCoordinate(1.0f, 0.0f))
                        .build()
                )
                // top left
                vertices.add(
                    Vertex.builder()
                        .setPosition(Vector3(-1.0f, 1.0f, 1.0f))
                        .setNormal(Vector3(0.0f, 0.0f, 1.0f))
                        .setUvCoordinate(Vertex.UvCoordinate(0.0f, 1.0f))
                        .build()
                )
                // top right
                vertices.add(
                    Vertex.builder()
                        .setPosition(Vector3(1.0f, 1.0f, 1.0f))
                        .setNormal(Vector3(0.0f, 0.0f, 1.0f))
                        .setUvCoordinate(Vertex.UvCoordinate(1.0f, 1.0f))
                        .build()
                )
                val triangleIndices: ArrayList<Int> = ArrayList()
                triangleIndices.add(0)
                triangleIndices.add(1)
                triangleIndices.add(2)
                triangleIndices.add(1)
                triangleIndices.add(3)
                triangleIndices.add(2)
                val submesh =
                    RenderableDefinition.Submesh.builder().setTriangleIndices(triangleIndices)
                        .setMaterial(material)
                        .build()
                ModelRenderable.builder().setSource(
                    RenderableDefinition.builder()
                        .setVertices(vertices)
                        .setSubmeshes(Arrays.asList(submesh))
                        .build()
                ).build().apply {
                    thenAccept { renderable ->
                        videoRenderable = renderable
                        renderable?.material?.apply {
                            setExternalTexture("cameraexture", texture)
                            setFloat4("keyColor", CHROMA_KEY_COLOR)
                            setBoolean("disableChromaKey", false)
                        }

                        val node = Node()
                        node.setParent(sceneView.scene)

                        // Set the scale of the node so that the aspect ratio of the video is correct.
                        val videoWidth = mediaPlayer.videoWidth.toFloat()
                        val videoHeight = mediaPlayer.videoHeight.toFloat()
                        node.localScale =
                            Vector3(
                                0.85f * (videoWidth / videoHeight),
                                0.85f,
                                1.0f
                            )

                        // Start playing the video when the first node is placed.
                        if (!mediaPlayer.isPlaying) {
                            // Wait to set the renderable until the first frame of the  video becomes available.
                            // This prevents the renderable from briefly appearing as a black quad before the video
                            // plays.
                            texture
                                .surfaceTexture
                                .setOnFrameAvailableListener { surfaceTexture: SurfaceTexture? ->
                                    node.renderable = videoRenderable
                                    texture.surfaceTexture.setOnFrameAvailableListener(null)
                                }

                            mediaPlayer.start()
                        } else {
                            node.renderable = videoRenderable
                        }
                    }
                        .exceptionally(
                            { throwable -> null })
                }
            }
    }

    override fun onPause() {
        super.onPause()
        sceneView.pause()
    }

    private fun addNodeToScene(model: ModelRenderable) {
        if (sceneView != null) {
            val transformationSystem = makeTransformationSystem()
            val dragTransformableNode = DragTransformableNode(1.0f, transformationSystem)
            dragTransformableNode.renderable = model
            sceneView.scene.addChild(dragTransformableNode)
            dragTransformableNode.select()
            sceneView.scene
                .addOnPeekTouchListener { hitTestResult: HitTestResult?, motionEvent: MotionEvent? ->
                    transformationSystem.onTouch(
                        hitTestResult,
                        motionEvent
                    )
                }
        }
    }

    private fun makeTransformationSystem(): TransformationSystem {
        val footprintSelectionVisualizer = FootprintSelectionVisualizer()
        return TransformationSystem(resources.displayMetrics, footprintSelectionVisualizer)
    }


    override fun onResume() {
        super.onResume()
        try {
            sceneView.resume()
        } catch (e: CameraNotAvailableException) {
            e.printStackTrace()
        }
    }

    object Statics {
        var EXTRA_MODEL_TYPE = "modelType"
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            sceneView.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
