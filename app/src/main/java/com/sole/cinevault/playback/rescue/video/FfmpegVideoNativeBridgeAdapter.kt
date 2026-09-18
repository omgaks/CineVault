package com.sole.cinevault.playback.rescue.video

/**
 * Adapts B16 decoder commands to the B17 native bridge and routes native
 * callbacks through the B15 generation-aware event router.
 */
class FfmpegVideoNativeBridgeAdapter(
    private val bridge: FfmpegVideoNativeBridge,
    private val eventRouter: FfmpegVideoDecodeEventRouter,
) : FfmpegVideoDecoderCommandSink {

    private var released = false

    fun create() {
        check(!released) { "native bridge adapter is released" }

        if (!bridge.isCreated) {
            bridge.create(
                FfmpegVideoNativeEventListener { event ->
                    eventRouter.route(event)
                },
            )
        }
    }

    override fun send(command: FfmpegVideoDecoderCommand) {
        check(!released) { "native bridge adapter is released" }

        if (command != FfmpegVideoDecoderCommand.Release) {
            check(bridge.isCreated) {
                "native bridge must be created before decoder commands"
            }
        }

        when (command) {
            is FfmpegVideoDecoderCommand.Prepare ->
                bridge.prepare(
                    generation = command.generation,
                    startPositionMs = command.startPositionMs,
                )

            FfmpegVideoDecoderCommand.Play ->
                bridge.play()

            FfmpegVideoDecoderCommand.Pause ->
                bridge.pause()

            is FfmpegVideoDecoderCommand.Seek ->
                bridge.seek(
                    generation = command.generation,
                    positionMs = command.positionMs,
                )

            FfmpegVideoDecoderCommand.Stop ->
                bridge.stop()

            FfmpegVideoDecoderCommand.Release -> {
                if (bridge.isCreated) {
                    bridge.release()
                }
                released = true
            }
        }
    }
}
