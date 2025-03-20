import fs from "node:fs";

const basePath = "."

function toPath(subPath) {
  return `${basePath}/${subPath}`;
}

export default function extractRecipes() {
  const recipes = []
  fs.readdirSync(toPath("tags/recipe")).forEach(file => {
    const path = toPath(`tags/recipe/${file}`);
    const content = JSON.parse(fs.readFileSync(path).toString());

    const recipe = {
      fromFile: file,
      type: content.type,
      ...extractRecipe(content)
    }

    recipes.push(recipe);
  })
  return recipes
}

function extractRecipe(content) {
  if (content.type.includes("special") || content.type === "minecraft:crafting_decorated_pot") {
    return {
      ignored: true
    }
  }
  switch (content.type) {
    case "minecraft:crafting_shaped":
      return extractCraftingShaped(content)
    case "minecraft:crafting_shapeless":
      return extractCraftingShapeless(content)
    case "minecraft:stonecutting":
    case "minecraft:smelting":
    case "minecraft:campfire_cooking":
    case "minecraft:smoking":
    case "minecraft:blasting":
      return extractSingleIngredient(content)
    case "minecraft:crafting_transmute":
      return extractTransmutation(content)
    case "minecraft:smithing_trim":
      return extractSmithingTrim(content)
    case "minecraft:smithing_transform":
      return extractSmithingTransform(content)
    default:
      return {
        error: "Could not extract recipe from type " + content.type,
        data: content
      }
  }
}

function extractCraftingShaped(content) {
  return {
    requirements: Object.entries(content.key).map(([key, item]) => {
      const count = content.pattern.map(pattern => pattern.split("").filter(patternKey => patternKey === key).length).reduce((prev, curr) => prev + curr, 0);
      return {
        item,
        count
      }
    }),
    result: {
      count: content.result.count,
      item: content.result.id
    }
  }
}

function extractCraftingShapeless(content) {
  return {
    requirements: Object.entries(content.ingredients.reduce((prev, curr) => ({
      ...prev,
      [curr]: prev[curr] ? prev[curr] + 1 : 1
    }), {})).map(([item, count]) => ({
      item,
      count
    })),
    result: {
      count: content.result.count,
      item: content.result.id
    }
  }
}

function extractSingleIngredient(content) {
  return {
    requirements: [{
      item: content.ingredient,
      count: 1
    }],
    result: {
      count: content.result.count,
      item: content.result.id
    }
  }
}

function extractTransmutation(content) {
  return {
    requirements: [
      {
        item: content.input,
        count: 1
      },
      {
        item: content.material,
        count: 1
      }
    ],
    result: {
      item: content.result,
      count: 1
    }
  }
}

function extractSmithingTrim(content) {
  return {
    requirements: [
      {
        item: content.base,
        count: 1
      },
      {
        item: content.addition,
        count: 1
      }
    ],
    result: {
      item: content.template,
      count: 1
    }
  }
}

function extractSmithingTransform(content) {
  return {
    requirements: [
      {
        item: content.base,
        count: 1
      },
      {
        item: content.addition,
        count: 1
      },
      {
        item: content.template,
        count: 1
      }
    ],
    result: {
      item: content.result.id,
      count: content.result.count
    }
  }
}