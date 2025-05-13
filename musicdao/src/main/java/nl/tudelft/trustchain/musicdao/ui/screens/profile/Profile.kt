package nl.tudelft.trustchain.musicdao.ui.screens.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import nl.tudelft.trustchain.musicdao.core.repositories.model.Album
import nl.tudelft.trustchain.musicdao.core.repositories.model.Artist
import nl.tudelft.trustchain.musicdao.ui.components.releases.NonLazyReleaseList
import nl.tudelft.trustchain.musicdao.ui.navigation.Screen

@ExperimentalFoundationApi
@ExperimentalMaterialApi
@Composable
fun Profile(
    artist: Artist,
    releases: List<Album> = listOf(),
    navController: NavController
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF77DF7C), Color(0xFF70C774))))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .align(Alignment.BottomStart)
            ) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.h6
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = artist.accountType.displayName,
                        style = MaterialTheme.typography.subtitle1,
                        color = Color.White,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.padding(bottom = 20.dp)) {
                OutlinedButton(onClick = { }, modifier = Modifier.padding(end = 10.dp)) {
                    Text(text = "Follow")
                }
                OutlinedButton(onClick = { navController.navigate(Screen.Donate.createRoute(publicKey = artist.publicKey)) }) {
                    Text(text = "Donate")
                }
            }

            Column(modifier = Modifier.padding(bottom = 20.dp)) {
                Text(text = "Account Status", fontWeight = FontWeight.Bold)
                Text(text = "Current Level: ${artist.accountType.displayName}")
                Text(text = "Download Delay: ${artist.accountType.downloadDelayHours} hours")
                Text(text = "Total Donations: ${artist.totalDonations} BTC")
                if (artist.accountType == AccountType.BASIC) {
                    Text(
                        text = "Required for Pro: ${AccountType.PRO.requiredDonations - artist.totalDonations} BTC more",
                        color = MaterialTheme.colors.error
                    )
                }
                OutlinedButton(
                    onClick = { navController.navigate(Screen.AccountUpgrade.route) },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(if (artist.accountType == AccountType.BASIC) "Upgrade to Pro" else "Manage Account")
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
}
