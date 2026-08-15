package app.mydear.android.tools

import android.content.Context
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

object AndroidToolExecutor {
    fun executeExternal(context: Context, proposal: ToolProposal): Result<Unit> = runCatching {
        val decision = ToolPolicy.evaluate(proposal)
        check(decision.allowed && decision.requiresConfirmation) { "허용되지 않은 동작이에요" }
        val activity = context as? Activity ?: error("현재 화면에서만 외부 앱을 열 수 있어요")
        val lifecycleOwner = activity as? LifecycleOwner ?: error("현재 화면 상태를 확인할 수 없어요")
        check(!activity.isFinishing && !activity.isDestroyed && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            "앱이 화면에 보일 때만 실행할 수 있어요"
        }
        val intent = when (proposal.name) {
            ToolName.PrepareCall -> Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", proposal.arguments.getValue("phone"), null))
            ToolName.PrepareAlarm -> Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, proposal.arguments.getValue("hour").toInt())
                putExtra(AlarmClock.EXTRA_MINUTES, proposal.arguments.getValue("minute").toInt())
                putExtra(AlarmClock.EXTRA_MESSAGE, proposal.arguments.getValue("label"))
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            }
            ToolName.PrepareMessage -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${proposal.arguments.getValue("phone")}")).apply {
                putExtra("sms_body", proposal.arguments.getValue("body"))
            }
            ToolName.OpenMap -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(proposal.arguments.getValue("query"))}"))
            else -> error("외부 앱 동작이 아니에요")
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        check(intent.resolveActivity(context.packageManager) != null) { "이 동작을 처리할 앱이 없어요" }
        context.startActivity(intent)
    }
}
