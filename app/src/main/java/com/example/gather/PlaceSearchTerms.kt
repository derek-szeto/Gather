package com.example.gather

import java.text.Normalizer
import java.util.Locale

object PlaceSearchTerms {

    data class Match(
        val categories: String,
        val businessName: String,
        val label: String = ""
    )

    private data class Group(
        val label: String,
        val categories: String,
        val aliases: List<String>
    )

    private val groups = listOf(
        Group(
            "Coffee shops",
            "catering.cafe",
            listOf(
                "cafe", "cafes", "coffee", "coffee shop", "coffee shops"
            )
        ),
        Group(
            "Restaurants",
            "catering.restaurant",
            listOf(
                "restaurant", "restaurants", "food", "dining"
            )
        ),
        Group(
            "Burgers",
            "catering.restaurant.burger,catering.fast_food.burger",
            listOf(
                "burger", "burgers", "hamburger", "hamburgers",
                "burger joint", "burger place", "burger restaurant",
                "cheeseburger", "cheeseburgers"
            )
        ),
        Group(
            "Pizza",
            "catering.restaurant.pizza,catering.fast_food.pizza",
            listOf(
                "pizza", "pizzeria", "pizzerias", "pizza place"
            )
        ),
        Group(
            "Parks",
            "leisure.park",
            listOf(
                "park", "parks"
            )
        ),
        Group(
            "Playgrounds",
            "leisure.playground",
            listOf(
                "playground", "playgrounds"
            )
        ),
        Group(
            "Picnic areas",
            "leisure.picnic",
            listOf(
                "picnic", "picnic spot", "picnic area"
            )
        ),
        Group(
            "Movie theaters",
            "entertainment.cinema",
            listOf(
                "movie", "movies", "cinema",
                "movie theater", "movie theatre"
            )
        ),
        Group(
            "Bowling",
            "entertainment.bowling_alley",
            listOf(
                "bowling", "bowling alley"
            )
        ),
        Group(
            "Museums",
            "entertainment.museum",
            listOf(
                "museum", "museums"
            )
        ),
        Group(
            "Arcades",
            "entertainment.amusement_arcade",
            listOf(
                "arcade", "arcades"
            )
        ),
        Group(
            "Aquariums",
            "entertainment.aquarium",
            listOf(
                "aquarium", "aquariums"
            )
        ),
        Group(
            "Zoos",
            "entertainment.zoo",
            listOf(
                "zoo", "zoos"
            )
        ),
        Group(
            "Art galleries",
            "entertainment.culture.gallery",
            listOf(
                "gallery", "art gallery"
            )
        ),
        Group(
            "Theaters",
            "entertainment.culture.theatre",
            listOf(
                "theater", "theatre", "performing arts"
            )
        ),
        Group(
            "Shopping malls",
            "commercial.shopping_mall",
            listOf(
                "mall", "shopping mall"
            )
        ),
        Group(
            "Grocery stores",
            "commercial.supermarket",
            listOf(
                "supermarket", "grocery", "grocery store"
            )
        ),
        Group(
            "Bookstores",
            "commercial.books",
            listOf(
                "bookstore", "book store", "books"
            )
        ),
        Group(
            "Sushi",
            "catering.restaurant.sushi",
            listOf(
                "sushi", "sushi restaurant"
            )
        ),
        Group(
            "Mexican food",
            "catering.restaurant.mexican",
            listOf(
                "mexican", "mexican food"
            )
        ),
        Group(
            "Italian food",
            "catering.restaurant.italian",
            listOf(
                "italian", "italian food"
            )
        ),
        Group(
            "Chinese food",
            "catering.restaurant.chinese",
            listOf(
                "chinese", "chinese food"
            )
        ),
        Group(
            "Indian food",
            "catering.restaurant.indian",
            listOf(
                "indian", "indian food"
            )
        ),
        Group(
            "Bubble tea",
            "catering.cafe.bubble_tea",
            listOf(
                "boba", "bubble tea"
            )
        ),
        Group(
            "Ice cream",
            "catering.ice_cream,catering.cafe.ice_cream",
            listOf(
                "ice cream", "ice cream shop"
            )
        ),
        Group(
            "Fast food",
            "catering.fast_food",
            listOf(
                "fast food", "takeout"
            )
        ),
        Group(
            "Tacos",
            "catering.restaurant.tacos,catering.fast_food.tacos",
            listOf(
                "taco", "tacos", "taco place"
            )
        ),
        Group(
            "Sandwiches",
            "catering.restaurant.sandwich,catering.fast_food.sandwich",
            listOf(
                "sandwich", "sandwiches", "sandwich shop"
            )
        ),
        Group(
            "Wings",
            "catering.restaurant.wings,catering.fast_food.wings",
            listOf(
                "wings", "chicken wings"
            )
        ),
        Group(
            "Ramen",
            "catering.restaurant.ramen,catering.fast_food.ramen",
            listOf(
                "ramen", "ramen shop"
            )
        ),
        Group(
            "Barbecue",
            "catering.restaurant.barbecue",
            listOf(
                "bbq", "barbecue", "barbeque"
            )
        )
    )

    val suggestions: List<String> = groups
        .flatMap {
            listOf(it.label) + it.aliases
        }
        .distinctBy {
            normalize(it)
        }
        .sortedBy {
            normalize(it)
        }

    fun resolve(query: String): Match {
        val cleaned = query
            .trim()
            .replace(
                Regex("(?i)\\s+near\\s+me$"),
                ""
            )
            .trim()

        val key = normalize(cleaned)

        val group = groups.firstOrNull { group ->
            normalize(group.label) == key ||
                    group.aliases.any {
                        normalize(it) == key
                    }
        }

        return if (group != null) {
            Match(
                categories = group.categories,
                businessName = "",
                label = group.label
            )
        } else {
            Match(
                categories =
                    "catering,commercial,entertainment,leisure,tourism,sport",
                businessName = cleaned
            )
        }
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(
            value,
            Normalizer.Form.NFD
        )
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}