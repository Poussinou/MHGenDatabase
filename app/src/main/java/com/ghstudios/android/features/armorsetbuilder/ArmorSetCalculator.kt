package com.ghstudios.android.features.armorsetbuilder

import android.util.Log
import com.ghstudios.android.data.DataManager
import com.ghstudios.android.data.classes.*
import java.util.*

// todo: move this to some sort of data constants location
private const val TORSO_UP_ID = 203 // Skilltree ID for the TorsoUp skill
private const val SECRET_ARTS_ID = 204 // Skilltree ID. Needs 10 points in the skill for +2 to all other skills
private const val TALISMAN_BOOST_ID = 205 // Skilltree Id. Needs 10 points in the skill for x2 talisman skills

/**
 * Calculates the skill totals of a given armor set, storing the results
 * in the "results" variable. After updating the armor set, call "recalculate()"
 * to update the results.
 */
class ArmorSetCalculator(val set: ArmorSet) {
    /**
     * Internal backing data for the list of results. This allows results
     * to be exposed as immutable
     */
    private val data = mutableListOf<SkillTreeInSet>()

    private var torsoUpCount: Int = 0

    /**
     * Contains the list of results. The contents are calculated
     * on construction, and updated everytime "recalculate()" is called.
     */
    val results: List<SkillTreeInSet> = Collections.unmodifiableList(data)

    init {
        recalculate()
    }

    /**
     * Adds any skills to the armor set's skill trees that were not there before, and removes those no longer there.
     * Adding decorations and armor does not update skilltrees unless this method is called
     */
    fun recalculate() {
        // Reset data
        data.clear()
        torsoUpCount = 0

        // A map of the skill trees in the set and their associated SkillTreePointsSets
        val skillTreeToResult = mutableMapOf<Long, SkillTreeInSet>()

        for (piece in set.pieces) {
            val idx = piece.idx
            Log.v("ASB", "Reading skills from armor piece $idx")

            val armorSkillTreePoints = getSkillsFromArmorPiece(piece) // A map of the current piece of armor's skills, localized so we don't have to keep calling it

            for (skillTreePoints in armorSkillTreePoints) {
                val skillTree = skillTreePoints.skillTree
                val points = skillTreePoints.points

                // Count TorsoUp occurrences
                if (skillTree.id == TORSO_UP_ID.toLong()) {
                    torsoUpCount++
                }

                // Retrieve the existing result row...or create if it doesn't exist
                val skillRow = skillTreeToResult.getOrPut(skillTree.id) {
                    Log.v("ASB", "Adding skill tree ${skillTree.name} to the list of Skill Trees in the armor set.")
                    SkillTreeInSet(skillTree)
                }

                // Set the points for the given "set slot"
                skillRow.setPoints(idx, points)
            }
        }

        // Add the results, and sort from largest to smallest
        data.addAll(skillTreeToResult.values)
        data.sortByDescending { it.getTotal() }
    }

    /**
     * A helper method that converts an armor piece present in the current set into a map of the skills it provides and the respective points in each.
     * @return A map of all the skills the armor piece provides along with the number of points in each.
     */
    private fun getSkillsFromArmorPiece(armorSetPiece: ArmorSetPiece): List<SkillTreePoints> {
        val dataManager = DataManager.get()

        val equipment = armorSetPiece.equipment
        val decorations = armorSetPiece.decorations

        // mapping of skilltree id to skilltree points. The values are returned in the end
        val skillCache = mutableMapOf<Long, SkillTreePoints>()

        // Get points from the equipment/talisman itself first
        if (equipment is ASBTalisman) {
            equipment.firstSkill?.let {
                skillCache[it.skillTree.id] = it
            }
            equipment.secondSkill?.let {
                skillCache[it.skillTree.id] = it
            }
        } else {
            val equipmentSkills = dataManager.queryItemToSkillTreeArrayItem(equipment.id)
            for (itemToSkillTree in equipmentSkills) { // We add skills for armor
                skillCache[itemToSkillTree.skillTree.id] = itemToSkillTree
            }
        }

        // Add decorations
        for (d in decorations) {
            val decorationSkills = dataManager.queryItemToSkillTreeArrayItem(d.id)
            for (itemToSkillTree in decorationSkills) {
                val skillTree = itemToSkillTree.skillTree
                val points = itemToSkillTree.points

                // SkillPoints are immmutable, so if we're adding, create a new entry
                val totalPoints = points + (skillCache[skillTree.id]?.points ?: 0)
                skillCache[skillTree.id] = SkillTreePoints(skillTree, totalPoints)
            }
        }

        return skillCache.values.toList()
    }

    /**
     * A container class that represents a skill tree as well as a specific number of points provided by each armor piece in a set.
     * TODO: More descriptive name
     */
    inner class SkillTreeInSet(val skillTree: SkillTree) {
        private val points = IntArray(6)

        fun getPoints(pieceIndex: Int): Int {
            return if (pieceIndex == ASBSession.BODY) {
                // TorsoUp stacks, so you multiply the skill * number of occurrences
                points[pieceIndex] * (torsoUpCount + 1)
            } else {
                points[pieceIndex]
            }
        }

        /**
         * @return The total number of skill points provided to the skill by all pieces in the set.
         */
        fun getTotal(): Int {
            var total = 0
            for (i in points.indices) {
                total += getPoints(i)
            }
            return total
        }

        fun setPoints(pieceIndex: Int, piecePoints: Int) {
            points[pieceIndex] = piecePoints
        }
    }
}