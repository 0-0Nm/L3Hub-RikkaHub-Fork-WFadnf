package me.rerere.rikkahub.ui.hooks

import org.junit.Assert.assertEquals
import org.junit.Test

class BusyComposerActionTest {
    @Test fun ordinaryIdleIsUnchangedForAllInputsAndGestures() {
        for (text in listOf(false, true)) for (attachments in listOf(false, true))
            for (longPress in listOf(false, true)) {
                assertEquals(BusyComposerAction.ORDINARY,
                    busyComposerAction(false, text, attachments, false, longPress))
            }
    }
    @Test fun busyShortTextDefaultsToTemporaryWithOrWithoutAttachments() {
        for (attachments in listOf(false, true)) {
            assertEquals(BusyComposerAction.TEMPORARY,
                busyComposerAction(true, true, attachments, false))
        }
    }
    @Test fun busyLongTextOpensChooserWithOrWithoutAttachments() {
        for (attachments in listOf(false, true)) {
            assertEquals(BusyComposerAction.CHOOSER,
                busyComposerAction(true, true, attachments, false, true))
        }
    }
    @Test fun attachmentOnlyCanQueueViaEitherGesture() {
        for (longPress in listOf(false, true)) {
            assertEquals(BusyComposerAction.CHOOSER,
                busyComposerAction(true, false, true, false, longPress))
        }
    }
    @Test fun emptyBusyInputKeepsStopBehavior() {
        for (longPress in listOf(false, true)) {
            assertEquals(BusyComposerAction.ORDINARY,
                busyComposerAction(true, false, false, false, longPress))
        }
    }
    @Test fun editingNeverBecomesGuidanceOrQueue() {
        for (busy in listOf(false, true)) for (text in listOf(false, true))
            for (attachments in listOf(false, true)) for (longPress in listOf(false, true)) {
                assertEquals(BusyComposerAction.ORDINARY,
                    busyComposerAction(busy, text, attachments, true, longPress))
            }
    }
}
