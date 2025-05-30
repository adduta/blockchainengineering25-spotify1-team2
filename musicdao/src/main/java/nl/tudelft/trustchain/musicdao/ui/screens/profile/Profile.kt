package nl.tudelft.trustchain.musicdao.ui.screens.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import nl.tudelft.trustchain.musicdao.core.repositories.model.Album
import nl.tudelft.trustchain.musicdao.core.repositories.model.Artist
import nl.tudelft.trustchain.musicdao.ui.components.releases.NonLazyReleaseList
import nl.tudelft.trustchain.musicdao.ui.navigation.Screen
import nl.tudelft.trustchain.musicdao.core.model.AccountType
import nl.tudelft.trustchain.musicdao.ui.components.TierStatusBadge
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.tudelft.trustchain.musicdao.ui.components.UpgradeDialog
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import nl.tudelft.trustchain.musicdao.MusicActivity
import dagger.hilt.android.EntryPointAccessors
import nl.tudelft.trustchain.musicdao.ui.screens.wallet.BitcoinWalletViewModel

@ExperimentalFoundationApi
@ExperimentalMaterialApi
@Composable
fun Profile(
    artist: Artist,
    releases: List<Album> = listOf(),
    navController: NavController,
    bitcoinWalletViewModel: BitcoinWalletViewModel
) {
    val viewModelFactory =
        EntryPointAccessors.fromActivity(
            LocalContext.current as Activity,
            MusicActivity.ViewModelFactoryProvider::class.java
        ).profileScreenViewModelFactory()

    val viewModel: ProfileScreenViewModel =
        viewModel(
            factory = ProfileScreenViewModel.provideFactory(
                viewModelFactory,
                publicKey = artist.publicKey,
                bitcoinWalletViewModel = bitcoinWalletViewModel
            )
        )

    val accountType by viewModel.accountType.collectAsState()
    val validUntil by viewModel.validUntil.collectAsState()
    var showUpgradeDialog by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF77DF7C),
                                Color(0xFF70C774)
                            )
                        )
                    )
        ) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.h6,
                modifier =
                    Modifier
                        .padding(20.dp)
                        .align(
                            Alignment.BottomStart
                        )
            )
        }

        Column(modifier = Modifier.padding(20.dp)) {
            // Account Status Section
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                elevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Account Status",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TierStatusBadge(
                            tier = accountType,
                            modifier = Modifier.padding(end = 8.dp)
                        )

                        Text(
                            text =
                                when (accountType) {
                                    AccountType.PRO -> "Pro Account"
                                    AccountType.BASIC -> "Basic Account"
                                },
                            fontSize = 16.sp
                        )
                    }

                    if (accountType == AccountType.PRO && validUntil != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val formattedDate =
                            validUntil?.let { instant ->
                                instant.atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
                            } ?: "Unknown"
                        Text(
                            text = "Valid until: $formattedDate",
                            fontSize = 14.sp,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (accountType == AccountType.BASIC) {
                        Button(
                            onClick = { showUpgradeDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("Upgrade to Pro")
                        }
                    }
                }
            }

            // Benefits Section
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                elevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Pro Benefits",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    ProBenefitItem(
                        title = "Instant Access",
                        description = "Get immediate access to new releases"
                    )

                    ProBenefitItem(
                        title = "No Waiting Period",
                        description = "Skip the 7-day waiting period for basic users"
                    )

                    ProBenefitItem(
                        title = "Support Artists",
                        description = "Directly support your favorite artists"
                    )
                }
            }

            Row(modifier = Modifier.padding(bottom = 20.dp)) {
                OutlinedButton(onClick = { }, modifier = Modifier.padding(end = 10.dp)) {
                    Text(text = "Follow")
                }
                OutlinedButton(onClick = {
                    navController.navigate(
                        Screen.Donate.createRoute(
                            publicKey = artist.publicKey
                        )
                    )
                }) {
                    Text(text = "Donate")
                }
            }

            Column(modifier = Modifier.padding(bottom = 20.dp)) {
                Text(text = "Releases", fontWeight = FontWeight.Bold)
                if (releases.isEmpty()) {
                    Text("No releases by this artist")
                } else {
                    NonLazyReleaseList(releasesState = releases, navController = navController)
                }
            }

            Column(modifier = Modifier.padding(bottom = 20.dp)) {
                Text(text = "Public Key", fontWeight = FontWeight.Bold)
                Text(text = artist.publicKey)
            }

            Column(modifier = Modifier.padding(bottom = 20.dp)) {
                Text(text = "Bitcoin Address", fontWeight = FontWeight.Bold)
                Text(text = artist.bitcoinAddress)
            }

            Column(modifier = Modifier.padding(bottom = 20.dp)) {
                Text(text = "Biography", fontWeight = FontWeight.Bold)
                Text(text = artist.biography)
            }
        }
    }

    if (showUpgradeDialog) {
        UpgradeDialog(
            onDismiss = { showUpgradeDialog = false },
            onUpgrade = {
                viewModel.upgradeToPro()
                showUpgradeDialog = false
            },
            currentBalance = bitcoinWalletViewModel.confirmedBalance.value
        )
    }
}

@Composable
private fun ProBenefitItem(
    title: String,
    description: String
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Star,
            contentDescription = null,
            tint = MaterialTheme.colors.primary,
            modifier = Modifier.padding(end = 8.dp)
        )
        Column {
            Text(
                text = title,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}
