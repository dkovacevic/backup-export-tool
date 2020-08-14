@file:Suppress("unused") // we need to force it to run inside transaction

package pw.forst.wire.backups.android.database.converters

import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import pw.forst.wire.backups.android.database.dto.ConversationAddMemberDto
import pw.forst.wire.backups.android.database.dto.ConversationLeaveMembersDto
import pw.forst.wire.backups.android.database.dto.ConversationMembersDto
import pw.forst.wire.backups.android.database.dto.ConversationsDataDto
import pw.forst.wire.backups.android.database.dto.DirectConversationDto
import pw.forst.wire.backups.android.database.dto.NamedConversationDto
import pw.forst.wire.backups.android.database.model.ConversationMembers
import pw.forst.wire.backups.android.database.model.Conversations
import pw.forst.wire.backups.android.database.model.Messages
import java.util.UUID


fun Transaction.getNamedConversations() = Conversations
    .slice(Conversations.id, Conversations.name)
    .select { Conversations.name.isNotNull() }
    .map {
        NamedConversationDto(
            it[Conversations.id].toUuid(),
            it[Conversations.name] ?: "no name"
        )
    }

fun Transaction.getDirectMessages(myId: UUID) = (Conversations leftJoin ConversationMembers)
    .slice(Conversations.id, Conversations.name, ConversationMembers.userId, ConversationMembers.conversationId)
    .select { Conversations.name.isNull() }
    .groupBy({ it[Conversations.id].toUuid() }, { it[ConversationMembers.userId].toUuid() })
    .mapValues { (_, values) -> values.firstOrNull { it != myId } ?: myId }
    .map { (conversationId, userId) -> DirectConversationDto(conversationId, otherUser = userId) }

fun Transaction.getConversationsData(): ConversationsDataDto {
    val addMembers = Messages
        .slice(Messages.messageType, Messages.conversationId, Messages.time, Messages.userId, Messages.members)
        .select { Messages.messageType eq "MemberJoin" }
        .map {
            ConversationAddMemberDto(
                conversationId = it[Messages.conversationId].toUuid(),
                timeStamp = it[Messages.time].toExportDateFromAndroid(),
                addingUser = it[Messages.userId].toUuid(),
                addedUsers = it[Messages.members]
                    ?.split(",")
                    ?.map(String::toUuid)
                    ?: emptyList()
            )
            }

    val leavingMembers = Messages
        .slice(Messages.messageType, Messages.conversationId, Messages.time, Messages.members)
        .select { Messages.messageType eq "MemberLeave" }
        .map {
            ConversationLeaveMembersDto(
                conversationId = it[Messages.conversationId].toUuid(),
                timeStamp = it[Messages.time].toExportDateFromAndroid(),
                leavingMembers = it[Messages.members]
                    ?.split(",")
                    ?.map(String::toUuid)
                    ?: emptyList()
            )
            }

    val members = ConversationMembers
        .slice(ConversationMembers.conversationId, ConversationMembers.userId)
        .selectAll()
        .map { it[ConversationMembers.conversationId].toUuid() to it[ConversationMembers.userId].toUuid() }
        .groupBy({ it.first }, { it.second })
        .map { (conversationId, members) -> ConversationMembersDto(conversationId, members) }

    return ConversationsDataDto(members, addMembers, leavingMembers)
}
