import {ResourcePackRequest, ResourcesService} from "../../api/resources";
import {createResourcePack} from "../actions/resourceActions";
import axios from "axios";
import config from "../../config/config";

export default async function Resources() {
    const {resourcePacks} = await axios.get(`${config.resourceServer}/api/v1/resource-pack`).then(res => res.data);

    return <>
        <form action={createResourcePack}>
            <label htmlFor={"resource-name-input"}>Navn</label>
            <input name={"name"} required minLength={3} id={"resource-name-input"} type={"text"} />
            <label htmlFor={"resource-version-input"}>Versjon</label>
            <input name={"version"} required pattern={"1.[1-9][0-9]{0,1}.[1-9]"} title={"Versjoner må følge format 1.x.x eller 1.xx.x. Snapshots og beta-versjoner er ikke støttet."} id={"resource-version-input"} type={"text"} />
            <label htmlFor={"resource-type-input"}>Type</label>
            <select name={"type"} required id={"resource-type-input"} defaultValue={ResourcePackRequest.type.FABRIC}>
                <option value={ResourcePackRequest.type.FABRIC}>Fabric</option>
                <option value={ResourcePackRequest.type.FORGE}>Forge</option>
            </select>
            <button type={"submit"}>Lag ny ressurs</button>
        </form>
        <ul>
            {resourcePacks.map(resource => <li key={`${resource.id}`}>{resource.name} ({resource.version})</li>)}
        </ul>
    </>
}