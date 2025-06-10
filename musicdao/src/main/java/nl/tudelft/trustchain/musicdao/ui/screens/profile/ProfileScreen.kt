package nl.tudelft.trustchain.musicdao.ui.screens.profile

import android.app.Activity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import nl.tudelft.trustchain.musicdao.MusicActivity
import nl.tudelft.trustchain.musicdao.ui.components.EmptyState
import nl.tudelft.trustchain.musicdao.core.model.AccountType
import nl.tudelft.trustchain.musicdao.ui.components.TierStatusBadge
import java.time.format.DateTimeFormatter
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.EntryPointAccessors
import nl.tudelft.trustchain.musicdao.ui.screens.wallet.BitcoinWalletViewModel

@ExperimentalMaterialApi
@ExperimentalFoundationApi
@Composable
fun ProfileScreen(
    publicKey: String,
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
            factory =
                ProfileScreenViewModel.provideFactory(
                    viewModelFactory,
                    publicKey = publicKey,
                    bitcoinWalletViewModel = bitcoinWalletViewModel
                )
        )

    val profile = viewModel.profile.collectAsState()
    val releases = viewModel.releases.collectAsState()

    profile.value?.let {
        Profile(
            artist = it,
            releases = releases.value,
            navController = navController,
            bitcoinWalletViewModel = bitcoinWalletViewModel
        )
    } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            firstLine = "404",
            secondLine = "This artist has not published  any information yet."
        )
        return
    }
}
