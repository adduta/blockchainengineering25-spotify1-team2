package nl.tudelft.trustchain.musicdao.ui.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.navigation.NavController
import nl.tudelft.trustchain.musicdao.ui.components.releases.ReleaseList

@ExperimentalFoundationApi
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SearchScreen(
    navController: NavController,
    screenViewModel: SearchScreenViewModel
) {
    val releases by screenViewModel.searchResult.collectAsState(listOf())
    val searchQuery by screenViewModel.searchQuery.collectAsState()

    Column {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                screenViewModel.searchDebounced(it)
            },
            placeholder = { Text("Search") },
            trailingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RectangleShape,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                backgroundColor = Color(0xFF222222),
                focusedBorderColor = Color(0xFF4CAF50), // green accent
                unfocusedBorderColor = Color(0xFF444444),
                textColor = Color.White,
                placeholderColor = Color(0xFFAAAAAA),
                trailingIconColor = Color(0xFFAAAAAA)
            )
        )
        ReleaseList(releasesState = releases, navController = navController)
    }
}
