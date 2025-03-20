import fs from "node:fs"
import extractRecipes from "./extract-recipes.mjs";
import extractLootTable from "./extract-loot-table.mjs";

const basePath = "."

function toPath(subPath) {
  return `${basePath}/${subPath}`;
}

const {
  id,
  pack_version: {
    resource,
    data
  }
} = JSON.parse(String(await fs.readFileSync(toPath("version.json"))))

const recipes = extractRecipes()
const lootTable = extractLootTable()

const exportedData = {
  version: id,
  resource_pack_version: resource,
  data_pack_version: data,
  lootTable,
  recipes
}

fs.writeFileSync(toPath("finalized/data.json"), JSON.stringify(exportedData, null, 2))