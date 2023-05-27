'use server';

import config from "../../config/config";
import {revalidateTag} from "next/cache";

export async function createResourcePack(formData) {
  const name = formData.get("name") as string;
  const version = formData.get("version") as string;
  const type = formData.get("type") as string;

  if(name && version && type) {
    try {
      await fetch(`${config.resourceServer}/api/v1/resource-pack`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Accept": "application/json"
        },
        next: {tags: ["packs"]},
        body: JSON.stringify({name, version, type})
      })
      revalidateTag("packs");
    } catch (e) {
      console.error(e);
    }
  }
}

