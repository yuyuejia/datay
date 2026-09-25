export interface IAuthority {
  name?: string;
  description?: string;
}

export class Authority implements IAuthority {
  constructor(
    public name?: string,
    public description?: string,
  ) {}
}
