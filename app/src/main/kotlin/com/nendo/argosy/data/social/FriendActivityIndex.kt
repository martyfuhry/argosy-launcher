package com.nendo.argosy.data.social

internal fun liveFriendActivity(friends: List<Friend>): Map<Int, List<FriendActivity>> =
    friends.asSequence()
        .filter { it.isAccepted && it.presence == PresenceStatus.IN_GAME }
        .mapNotNull { friend ->
            friend.currentGame?.igdbId?.let { igdbId -> igdbId to friend.toActivity() }
        }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })

private fun Friend.toActivity(): FriendActivity = FriendActivity(
    friendId = id,
    displayName = displayName.ifBlank { username },
    avatarColor = avatarColor,
    quayPassAvatar = quayPassAvatar,
    playingNow = true
)
