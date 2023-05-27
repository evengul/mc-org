/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

export type ResourceRequest = {
  name: string;
  type: ResourceRequest.type;
  url: string;
};

export namespace ResourceRequest {

  export enum type {
    MOD = 'MOD',
    RESOURCE_PACK = 'RESOURCE_PACK',
    DATA_PACK = 'DATA_PACK',
  }


}
