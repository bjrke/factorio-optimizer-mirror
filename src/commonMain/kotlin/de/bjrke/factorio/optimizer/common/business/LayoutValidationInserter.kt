package de.bjrke.factorio.optimizer.common.business

/**
 * Every inserter needs a source behind it and a drop in front of it, both a machine, a transport or
 * a chest; a transport must carry the inserter's material.
 */
internal fun validateInserters(
    index: LayoutIndex,
    entities: List<LayoutEntity>,
    errors: MutableList<String>,
) {
    for (inserter in entities.filterIsInstance<InserterEntity>()) {
        checkInserter(inserter, index, errors)
    }
}

private fun checkInserter(
    inserter: InserterEntity,
    index: LayoutIndex,
    errors: MutableList<String>,
) {
    val tile = inserter.area.topLeft
    // a long inserter reaches one tile further, but still drops right beside itself
    val reach = if (inserter.long) 2 else 1
    checkSource(index.at(tile + inserter.direction.step * reach), inserter, errors)
    checkDrop(index.at(tile - inserter.direction.step), inserter, errors)
}

private fun checkSource(
    source: LayoutEntity?,
    inserter: InserterEntity,
    errors: MutableList<String>,
) {
    if (!canHold(source)) {
        errors += "inserter at ${inserter.area.topLeft} has no source"
    } else if (source is TransportEntity && source.material != inserter.material) {
        errors +=
            "inserter at ${inserter.area.topLeft} draws ${source.material.name} " +
                "instead of ${inserter.material.name}"
    }
}

private fun checkDrop(
    drop: LayoutEntity?,
    inserter: InserterEntity,
    errors: MutableList<String>,
) {
    if (!canHold(drop)) {
        errors += "inserter at ${inserter.area.topLeft} has no drop"
    } else if (drop is TransportEntity && drop.material != inserter.material) {
        errors +=
            "inserter at ${inserter.area.topLeft} drops ${drop.material.name} " +
                "instead of ${inserter.material.name}"
    }
}

/** Whether an inserter end can take or leave items here: a machine, a transport or a chest. */
private fun canHold(end: LayoutEntity?) =
    end is MachineEntity || end is TransportEntity || end is ChestLikeEntity
