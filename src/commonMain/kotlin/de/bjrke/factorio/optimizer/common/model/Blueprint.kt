package de.bjrke.factorio.optimizer.common.model

/** A cardinal facing, stored in a blueprint as 0/4/8/12 (see the entity JSON writer). */
enum class Direction(val step: Coordinate) {
    NORTH(Coordinate.north),
    EAST(Coordinate.east),
    SOUTH(Coordinate.south),
    WEST(Coordinate.west);

    /** The facing 180° away from this one. */
    fun opposite() =
        when (this) {
            NORTH -> SOUTH
            EAST -> WEST
            SOUTH -> NORTH
            WEST -> EAST
        }
}

/** Which end of a directional entity (an underground belt or a loader) this is: its `type`. */
enum class Io {
    INPUT,
    OUTPUT,
}

/**
 * One entity of a blueprint. [direction] is only set for entities that face a way.
 *
 * [type] is `input`/`output` for an underground belt (or pipe-to-ground), [recipe] the recipe an
 * assembling machine runs and [marker] the material a constant-combinator marker carries.
 */
data class BlueprintEntity(
    val name: String,
    val area: Area,
    val direction: Direction? = null,
    val recipe: String? = null,
    val type: Io? = null,
    val marker: Material? = null,
    /**
     * Whether [marker] is a fluid; a constant combinator needs an explicit signal type for those.
     */
    val markerFluid: Boolean = false,
    /**
     * The `entity_number`s this entity is wired to (power poles connect this way; a blueprint does
     * not rebuild those wires on its own).
     */
    val neighbours: List<Int> = emptyList(),
    /** Set on an infinity chest: the item it provides or the fact that it voids everything. */
    val infinity: InfinitySettings? = null,
    /** Set on an infinity pipe: the fluid it supplies. */
    val infinityPipe: InfinityPipeSettings? = null,
    /** Set on a chest: the logistic request it keeps in stock. */
    val requestFilter: RequestFilter? = null,
    /** Set on a chest: the inventory bar limit, e.g. the four slots a wooden chest is capped to. */
    val bar: Int? = null,
    /** Set on an inserter: it is the mirrored variant of the one facing [direction]. */
    val mirror: Boolean = false,
    /** Set on an inserter: the circuit condition that enables it. */
    val circuitCondition: CircuitCondition? = null,
    /** Set on a chest: whether its own circuit condition is enabled. */
    val circuitConditionEnabled: Boolean? = null,
) {

    constructor(
        name: String,
        topLeft: Coordinate,
        direction: Direction? = null,
        recipe: String? = null,
        type: Io? = null,
        marker: Material? = null,
        markerFluid: Boolean = false,
        neighbours: List<Int> = emptyList(),
        /** Set on an infinity chest: the item it provides or the fact that it voids everything. */
        infinity: InfinitySettings? = null,
        /** Set on an infinity pipe: the fluid it supplies. */
        infinityPipe: InfinityPipeSettings? = null,
        /** Set on a chest: the logistic request it keeps in stock. */
        requestFilter: RequestFilter? = null,
        /**
         * Set on a chest: the inventory bar limit, e.g. the four slots a wooden chest is capped to.
         */
        bar: Int? = null,
        /** Set on an inserter: it is the mirrored variant of the one facing [direction]. */
        mirror: Boolean = false,
        /** Set on an inserter: the circuit condition that enables it. */
        circuitCondition: CircuitCondition? = null,
        /** Set on a chest: whether its own circuit condition is enabled. */
        circuitConditionEnabled: Boolean? = null,
    ) : this(
        name = name,
        area = topLeft.area(1, 1),
        direction = direction,
        recipe = recipe,
        type = type,
        marker = marker,
        markerFluid = markerFluid,
        neighbours = neighbours,
        infinity = infinity,
        infinityPipe = infinityPipe,
        requestFilter = requestFilter,
        bar = bar,
        mirror = mirror,
        circuitCondition = circuitCondition,
        circuitConditionEnabled = circuitConditionEnabled,
    )

    fun isPowered(): Boolean = recipe != null || name.endsWith("inserter")
}

/**
 * An infinity chest's settings. With an [item] the chest keeps [item] available (a test input);
 * without one and [removeUnfiltered] it simply deletes whatever reaches it (a test output).
 */
data class InfinitySettings(val item: Material? = null, val removeUnfiltered: Boolean = false)

/**
 * An infinity pipe's settings: it keeps [fluid] available at 100% (the game writes 100% as
 * `percentage = 1`) and at [temperature]. `at-least` means the pipe tops the fluid up whenever it
 * drops below the percentage, so a connected factory can always draw from it.
 */
data class InfinityPipeSettings(
    val fluid: Material,
    val temperature: Int,
    val percentage: Int = 1,
    val mode: String = "at-least",
)

/**
 * A chest's logistic request: it keeps [count] of the item [name] available (a buffer chest
 * requests it, a storage chest filters it). [trashNotRequested] mirrors the buffer chest's "trash
 * unrequested" flag.
 */
data class RequestFilter(
    val name: String,
    val count: Int,
    val trashNotRequested: Boolean = false,
)

/**
 * An inserter's circuit condition: it runs while the item [name] compares [comparator] to
 * [constant].
 */
data class CircuitCondition(val name: String, val comparator: String, val constant: Int)

/**
 * One circuit network wire: the two entities it joins (their `entity_number`) and the connector
 * each end plugs into. A chest's circuit condition reads its contents, so the inserters reading it
 * must be wired to it. A wire is undirected; [first] and [second] just name its ends.
 */
data class BlueprintWire(
    val first: Int,
    val firstConnector: Int,
    val second: Int,
    val secondConnector: Int,
)

/** A whole blueprint: its [label], an optional [icon] signal, its [entities] and their [wires]. */
data class Blueprint(
    val label: String,
    val icon: Material? = null,
    val entities: List<BlueprintEntity> = emptyList(),
    val wires: List<BlueprintWire> = emptyList(),
)
