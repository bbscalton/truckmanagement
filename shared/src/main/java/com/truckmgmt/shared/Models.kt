package com.truckmgmt.shared

data class LatLngPoint(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val accuracyM: Float? = null,
    val speedMps: Float? = null,
    val ts: Long = 0L,
    val stopFlag: Boolean = false,
)

data class DeliveryStatusLabels(
    val value: String,
) {
    companion object {
        fun isActive(status: String): Boolean = status in setOf(
            TruckMgmtConstants.STATUS_ASSIGNED,
            TruckMgmtConstants.STATUS_ACCEPTED_BY_DRIVER,
            TruckMgmtConstants.STATUS_EN_ROUTE,
            TruckMgmtConstants.STATUS_ARRIVED,
            TruckMgmtConstants.STATUS_DELIVERED,
            TruckMgmtConstants.STATUS_PAYMENT_PENDING,
        )

        fun customerCanSeeTruck(status: String): Boolean = status in setOf(
            TruckMgmtConstants.STATUS_ACCEPTED_BY_DRIVER,
            TruckMgmtConstants.STATUS_EN_ROUTE,
            TruckMgmtConstants.STATUS_ARRIVED,
            TruckMgmtConstants.STATUS_DELIVERED,
            TruckMgmtConstants.STATUS_PAYMENT_PENDING,
            TruckMgmtConstants.STATUS_PAYMENT_ACCEPTED,
            TruckMgmtConstants.STATUS_CLOSED,
        )
    }
}
