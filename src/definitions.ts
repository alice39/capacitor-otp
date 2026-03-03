export interface OtpPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
