package cn.gov.xivpn2.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import cn.gov.xivpn2.BuildConfig
import cn.gov.xivpn2.R


@Composable
fun MyListItem(icon: Int, text: String, supportingText: String? = null, onClick: (() -> Unit)? = null) {
    var m = Modifier.fillMaxWidth()
    if (onClick != null) m = m.clickable(onClick = onClick)
    ListItem(
        leadingContent = {
            Icon(
                painter = painterResource(icon),
                tint = MaterialTheme.colorScheme.primary,
                contentDescription = "icon",
                modifier = Modifier.size(24.dp)
            )
        },
        headlineContent = { Text(text) },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        supportingContent = { if (supportingText != null) Text(supportingText) },
        modifier = m
    )
}


class AllSettingsFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_all_settings, container, false)
    }


    private fun openUrl(url: String) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(browserIntent)
        } catch (e: ActivityNotFoundException) {
            Log.e("SettingsFragment", "open browser", e)
        }
    }

    private fun showDonationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.donation)
            .setItems(R.array.donation, { dialog, which ->
                var walletAddress = ""
                when (which) {
                    0 -> walletAddress =
                        "TTpzvVJ7cv2RZVihd48GGZXg1896WFgQuJ"

                    1 -> walletAddress =
                        "0x593065aDE108505356abaD9c58bE950115678593"

                    2 -> walletAddress =
                        "CyRPKfkGnrAtVKijorcFYocL5fY37tX9j1atm2m8cY8m"

                    3 -> walletAddress =
                        "84iR4Tz29wFKxDpceeFhZQc3msh7N59PdNqxhEY9HjtZKs7wHqGLhw5AJ5p5zkxHMpU7DKHmhjjHmV7jaoVteoWsQs81tf3"

                    4 -> {
                        openUrl("https://github.com/sponsors/Exclude0122")
                        return@setItems
                    }
                }

                val finalWalletAddress = walletAddress
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.wallet_address)
                    .setMessage(walletAddress)
                    .setPositiveButton(R.string.copy, { dialog1, which1 ->
                        val clipboardManager: ClipboardManager =
                            requireContext().getSystemService(ClipboardManager::class.java)
                        clipboardManager.setPrimaryClip(
                            ClipData.newPlainText(
                                "Wallet Address",
                                finalWalletAddress
                            )
                        )
                    })
                    .show()
            })
            .show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val composeView = view.findViewById<ComposeView>(R.id.compose_view)

        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this))

        composeView.setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.background)
                        .padding(12.dp),
                ) {


                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {

                            MyListItem(R.drawable.baseline_cloud_24, getString(R.string.subscriptions)) {
                                startActivity(Intent(requireContext(), SubscriptionsActivity::class.java))
                            }

                            MyListItem(R.drawable.baseline_alt_route_24, getString(R.string.rules)) {
                                startActivity(Intent(requireContext(), RulesActivity::class.java))
                            }

                            MyListItem(R.drawable.baseline_dns_24, getString(R.string.dns)) {
                                startActivity(Intent(requireContext(), DNSActivity::class.java))
                            }

                            MyListItem(R.drawable.build, getString(R.string.dns_toolbox)) {
                                startActivity(Intent(requireContext(), DNSToolboxActivity::class.java))
                            }
                        }
                    }

                    Text(
                        text = getString(R.string.preferences),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp)
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Column {

                            MyListItem(R.drawable.baseline_settings_24, getString(R.string.preferences)) {
                                startActivity(Intent(requireContext(), PreferenceActivity::class.java))
                            }

                        }
                    }

                    Text(
                        text = getString(R.string.about),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp)
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Column {

                            MyListItem(
                                R.drawable.info,
                                getString(R.string.app_version),
                                supportingText = BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"
                            )

                            MyListItem(
                                R.drawable.article,
                                getString(R.string.privacy_policy),
                            ) {
                                openUrl("https://exclude0122.github.io/docs/privacy-policy.html")
                            }

                            MyListItem(
                                R.drawable.bug_report,
                                getString(R.string.feedback),
                            ) {
                                openUrl("https://github.com/Exclude0122/xivpn/issues/new")
                            }

                            MyListItem(
                                R.drawable.code,
                                getString(R.string.source_code),
                            ) {
                                openUrl("https://github.com/Exclude0122/xivpn")
                            }

                            MyListItem(
                                R.drawable.library_books,
                                getString(R.string.open_source_licenses),
                            ) {
                                startActivity(Intent(requireContext(), LicensesActivity::class.java))
                            }

                            MyListItem(
                                R.drawable.favorite,
                                getString(R.string.donation),
                                supportingText = getString(R.string.donation_summary)
                            ) {
                                showDonationDialog()
                            }

                        }
                    }

                }

            }
        }
    }

}