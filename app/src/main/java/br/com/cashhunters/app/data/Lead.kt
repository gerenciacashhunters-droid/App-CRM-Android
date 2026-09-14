package br.com.cashhunters.app.data

data class Lead(
    val id: String,
    val name: String,
    val phone: String?,
    val stage: String?,
    val updatedAt: String?
)

data class Session(val accessToken: String)
