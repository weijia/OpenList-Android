package com.openlist.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.openlist.app.data.AccountData
import com.openlist.app.data.AccountDataStore
import com.openlist.app.ui.theme.OpenListAppTheme
import kotlinx.coroutines.launch
import java.util.UUID

class AccountManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenListAppTheme {
                AccountManagerScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManagerScreen() {
    val context = LocalContext.current
    val accountDataStore = remember { AccountDataStore(context) }
    val scope = rememberCoroutineScope()

    val accounts = remember { mutableStateListOf<AccountData>() }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<AccountData?>(null) }

    // 加载账号
    LaunchedEffect(Unit) {
        val loadedAccounts = accountDataStore.getAccounts()
        accounts.clear()
        accounts.addAll(loadedAccounts)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账号管理") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加账号")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            items(accounts, key = { it.id }) { account ->
                AccountItem(
                    account = account,
                    onEdit = { editingAccount = account },
                    onDelete = {
                        accounts.remove(account)
                        scope.launch {
                            accountDataStore.deleteAccount(account.id)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (showAddDialog) {
        AccountDialog(
            account = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, username, password, serverUrl ->
                val newAccount = AccountData(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    username = username,
                    password = password,
                    serverUrl = serverUrl
                )
                accounts.add(newAccount)
                scope.launch {
                    accountDataStore.saveAccount(newAccount)
                }
                showAddDialog = false
            }
        )
    }

    editingAccount?.let { account ->
        AccountDialog(
            account = account,
            onDismiss = { editingAccount = null },
            onConfirm = { name, username, password, serverUrl ->
                val updatedAccount = account.copy(
                    name = name,
                    username = username,
                    password = password,
                    serverUrl = serverUrl
                )
                val index = accounts.indexOfFirst { it.id == account.id }
                if (index != -1) {
                    accounts[index] = updatedAccount
                    scope.launch {
                        accountDataStore.saveAccount(updatedAccount)
                    }
                }
                editingAccount = null
            }
        )
    }
}

@Composable
fun AccountItem(
    account: AccountData,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleMedium
                )
                if (account.serverUrl.isNotEmpty()) {
                    Text(
                        text = account.serverUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "编辑")
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除")
            }
        }
    }
}

@Composable
fun AccountDialog(
    account: AccountData?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, username: String, password: String, serverUrl: String) -> Unit
) {
    var name by remember { mutableStateOf(account?.name ?: "") }
    var username by remember { mutableStateOf(account?.username ?: "") }
    var password by remember { mutableStateOf(account?.password ?: "") }
    var serverUrl by remember { mutableStateOf(account?.serverUrl ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (account == null) "添加账号" else "编辑账号") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("账号名称") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名 (可选)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码 (可选)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("服务器地址 (可选)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, username, password, serverUrl) },
                enabled = name.isNotEmpty()
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
