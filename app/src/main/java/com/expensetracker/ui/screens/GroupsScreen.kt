package com.expensetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.expensetracker.data.model.ExpenseGroup
import com.expensetracker.ui.components.GlassBackground
import com.expensetracker.ui.components.GlassCard
import com.expensetracker.ui.components.GlassSectionHeader
import com.expensetracker.ui.components.GradientPillButton
import com.expensetracker.viewmodel.GroupUiState
import com.expensetracker.viewmodel.GroupViewModel

@Composable
fun GroupsScreen(
    viewModel: GroupViewModel,
    navController: NavController,
    onSignInRequired: () -> Unit
) {
    val groups by viewModel.myGroups.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState) {
        when (val s = uiState) {
            is GroupUiState.Error -> { errorMessage = s.message; viewModel.resetUiState() }
            else -> {}
        }
    }

    GlassBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "Groups",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GradientPillButton(
                        text = "Create group",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (!viewModel.isSignedIn) onSignInRequired()
                            else showCreateDialog = true
                        }
                    )
                    GradientPillButton(
                        text = "Join group",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (!viewModel.isSignedIn) onSignInRequired()
                            else showJoinDialog = true
                        }
                    )
                }
            }

            val activeGroups = groups.filter { !it.isClosed }
            val closedGroups = groups.filter { it.isClosed }

            if (activeGroups.isNotEmpty()) {
                item { GlassSectionHeader(title = "Active") }
                items(activeGroups) { group ->
                    GroupCard(group = group, onClick = {
                        navController.navigate("group_detail/${group.code}")
                    })
                }
            }

            if (closedGroups.isNotEmpty()) {
                item { GlassSectionHeader(title = "Archived") }
                items(closedGroups) { group ->
                    GroupCard(group = group, onClick = {
                        navController.navigate("group_detail/${group.code}")
                    })
                }
            }

            if (groups.isEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Group,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.White.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No groups yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Create a group for a trip or outing,\nor join one with a code.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            errorMessage?.let { msg ->
                item {
                    Text(msg, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateGroupDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                showCreateDialog = false
                viewModel.createGroup(name) { code ->
                    navController.navigate("group_detail/$code")
                }
            }
        )
    }

    if (showJoinDialog) {
        JoinGroupDialog(
            onDismiss = { showJoinDialog = false },
            onJoin = { code ->
                showJoinDialog = false
                viewModel.joinGroup(code) { _ ->
                    navController.navigate("group_detail/${code.trim().uppercase()}")
                }
            }
        )
    }
}

@Composable
private fun GroupCard(group: ExpenseGroup, onClick: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (group.isClosed) Icons.Default.Lock else Icons.Default.Group,
                    contentDescription = null,
                    tint = if (group.isClosed) Color.White.copy(alpha = 0.4f) else Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        group.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        "Code: ${group.code}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
            if (group.isClosed) {
                Text(
                    "Closed",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun CreateGroupDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create group") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Group name (e.g. Goa Trip)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            GradientPillButton(
                text = "Create",
                enabled = name.isNotBlank(),
                onClick = { onCreate(name.trim()) }
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun JoinGroupDialog(onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join group") },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase() },
                label = { Text("Enter group code (e.g. GOAT-4829)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            GradientPillButton(
                text = "Join",
                enabled = code.isNotBlank(),
                onClick = { onJoin(code.trim()) }
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
