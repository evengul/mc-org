package no.mcorg.infrastructure.repository

import no.mcorg.domain.*
import org.postgresql.shaded.com.ongres.scram.common.bouncycastle.pbkdf2.Pack

class PacksImpl(private val config: AppConfiguration) : Packs, Repository(config) {
    override fun getPack(id: Int): ResourcePack? {
        val pack = getConnection()
            .prepareStatement("select name,version,server_type from resource_pack where id = ?")
            .apply { setInt(1, id) }
            .executeQuery()
            .let {
                if (!it.next()) return null

                ResourcePack(id, it.getString(1), it.getString(2), it.getString(3).toServerType(), mutableListOf())
            }

        getConnection()
            .prepareStatement("select id,name,type,download_url from resource where pack_id = ?")
            .apply { setInt(1, id) }
            .executeQuery()
            .apply {
                while (next()) {
                    pack.resources.add(
                        Resource(
                            getInt(1),
                            getString(2),
                            getString(3).toResourceType(),
                            getString(4)
                        )
                    )
                }
            }

        return pack
    }

    override fun createPack(name: String, version: String, serverType: ServerType) {
        getConnection()
            .prepareStatement("insert into resource_pack (name, version, server_type) values (?, ?, ?)")
            .apply { setString(1, name); setString(2, version); setString(3, serverType.name) }
            .executeUpdate()
    }

    override fun getUserPacks(username: String): List<ResourcePack> {
        TODO("Not yet implemented")
    }

    override fun changePackName(id: Int, name: String) {
        getConnection()
            .prepareStatement("update resource_pack set name = ? where id = ?")
            .apply { setString(1, name); setInt(2, id) }
            .executeUpdate()
    }

    override fun addResource(packId: Int, name: String, type: ResourceType, downloadUrl: String) {
        getConnection()
            .prepareStatement("insert into resource (pack_id, name, type, download_url) values (?, ?, ?, ?)")
            .apply {
                setInt(1, packId)
                setString(2, name)
                setString(3, type.name)
                setString(4, downloadUrl)
            }.executeUpdate()
    }

    override fun removeResource(id: Int) {
        getConnection()
            .prepareStatement("delete from resource where id = ?")
            .apply { setInt(1, id) }
            .executeUpdate()
    }

    override fun upgradePack(id: Int, newVersion: String): ResourcePack {
        TODO("Not yet implemented")
    }

    private fun String.toServerType(): ServerType {
        when {
            this == "VANILLA" -> return ServerType.VANILLA
            this == "FABRIC" -> return ServerType.FABRIC
            this == "FORGE" -> return ServerType.FORGE
        }

        throw IllegalArgumentException("Unknown server type: $this")
    }

    private fun String.toResourceType(): ResourceType {
        when {
            this == "MOD" -> return ResourceType.MOD
            this == "MOD_PACK" -> return ResourceType.MOD_PACK
            this == "TEXTURE_PACK" -> return ResourceType.TEXTURE_PACK
            this == "DATA_PACK" -> return ResourceType.DATA_PACK
        }

        throw IllegalArgumentException("Unknown resource type $this")
    }
}