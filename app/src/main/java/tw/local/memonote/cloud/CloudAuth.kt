package tw.local.memonote.cloud

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import java.io.IOException
import java.util.concurrent.TimeUnit

class AuthorizationNeeded : IOException("Google Drive 需要重新授權")

object CloudAuth {
    fun request(accountName: String = ""): AuthorizationRequest {
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(CloudBackupState.DRIVE_SCOPE)))
        if (accountName.isNotBlank()) builder.setAccount(Account(accountName, "com.google"))
        return builder.build()
    }

    /** Called only from a background thread. A worker never launches consent UI. */
    fun token(context: Context): String {
        val result = Tasks.await(
            Identity.getAuthorizationClient(context).authorize(
                request(CloudBackupState.account(context))
            ),
            30, TimeUnit.SECONDS
        )
        if (result.hasResolution()) throw AuthorizationNeeded()
        return result.accessToken?.takeIf { it.isNotBlank() } ?: throw AuthorizationNeeded()
    }

    @Suppress("DEPRECATION")
    fun accountName(result: AuthorizationResult): String =
        result.toGoogleSignInAccount()?.email.orEmpty()
}