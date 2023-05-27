/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

export type ResourcePackRequest = {
  name: string;
  version: string;
  type: ResourcePackRequest.type;
};

export namespace ResourcePackRequest {

  export enum type {
    FABRIC = 'FABRIC',
    FORGE = 'FORGE',
  }


}
