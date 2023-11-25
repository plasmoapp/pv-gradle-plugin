package su.plo.voice.plugin.extension

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

fun MethodVisitor.visitCodeThenEnd(runnable: Runnable) {
    visitCode()

    runnable.run()

    visitInsn(Opcodes.RETURN)
    visitMaxs(0, 0)
    visitEnd()
}
