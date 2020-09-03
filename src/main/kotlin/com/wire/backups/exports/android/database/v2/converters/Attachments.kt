package com.wire.backups.exports.android.database.v2.converters

import com.wire.backups.exports.android.database.dto.AttachmentDto
import com.wire.backups.exports.android.database.v2.loaders.BackupExport
import com.wire.backups.exports.utils.mapCatching
import com.wire.backups.exports.utils.rowExportFailed
import pw.forst.tools.katlib.filterNotNullBy

internal fun BackupExport.getAttachments() =
    messages.values
        .filterNotNullBy { it.assetId }
        .mapNotNull { message ->
            assets[message.assetId]?.let { it to message }
        }.mapCatching({ (asset, message) ->
            AttachmentDto(
                id = message.id.toUuid(),
                conversationId = message.conversationId.toUuid(),
                name = asset.name,
                sender = message.userId.toUuid(),
                timestamp = message.time.toExportDateFromAndroid(),
                contentLength = asset.size,
                mimeType = asset.mime,
                assetToken = requireNotNull(asset.token) { "Asset token was null!" },
                assetKey = asset.id,
                sha = requireNotNull(asset.sha) { "Asset SHA was null!" },
                protobuf = requireNotNull(message.protos) { "Protobuf for asset was null!" }
            )
        }, rowExportFailed)
