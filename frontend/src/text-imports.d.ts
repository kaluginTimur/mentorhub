// The global stylesheet read as text through the Angular builder's `with { loader: 'text' }` import attribute.
declare module '*/styles.scss' {
  const text: string;
  export default text;
}
