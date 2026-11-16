package com.sampcity.social.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable private fun Soon(t: String) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(t) }
@Composable fun ReelsScreen(start: Int?, author: String?) = Soon("Reels")
@Composable fun ChatsScreen() = Soon("Mensajes")
@Composable fun NotificationsScreen() = Soon("Avisos")
sealed interface ChatTarget { data class Player(val name: String) : ChatTarget; data class Room(val id: Int) : ChatTarget }
@Composable fun ChatScreen(t: ChatTarget) = Soon("Chat")
@Composable fun GroupScreen(id: Int) = Soon("Grupo")
@Composable fun SearchScreen(q: String) = Soon("Buscar")
@Composable fun FriendsScreen() = Soon("Amigos")
@Composable fun GroupsScreen() = Soon("Grupos")
@Composable fun EventsScreen() = Soon("Eventos")
@Composable fun MarketScreen() = Soon("Marketplace")
@Composable fun SavedScreen() = Soon("Guardados")
@Composable fun ContentScreen(type: String) = Soon("Contenido $type")
@Composable fun StoryScreen(author: String) = Soon("Historia")
@Composable fun StaffScreen() = Soon("Staff")
@Composable fun TopScreen() = Soon("Top")
@Composable fun SettingsScreen() = Soon("Configuración")
@Composable fun PanelScreen() = Soon("Panel")
