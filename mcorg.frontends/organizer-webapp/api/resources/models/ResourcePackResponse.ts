/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

import type { ResourceResponse } from './ResourceResponse';

export type ResourcePackResponse = {
  id: string;
  name: string;
  version: string;
  serverType: ResourcePackResponse.serverType;
  resources: Array<ResourceResponse>;
};

export namespace ResourcePackResponse {

  export enum serverType {
    FABRIC = 'FABRIC',
    FORGE = 'FORGE',
  }


}
