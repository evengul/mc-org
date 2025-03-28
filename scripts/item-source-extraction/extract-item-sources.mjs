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
    resource: resourcePackVersion,
    data: dataPackVersion
  }
} = JSON.parse(String(await fs.readFileSync(toPath("version.json"))))

const recipes = extractRecipes()
const lootTable = extractLootTable()

const exportedData = {
  version: id,
  resourcePackVersion,
  dataPackVersion,
  lootTable,
  recipes
}

fs.writeFileSync(toPath("finalized/data.json"), JSON.stringify(exportedData, null, 2))