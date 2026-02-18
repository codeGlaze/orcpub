/**
 * React 18 APIs missing from cljsjs/react-dom 18.3.1-1 externs.
 * Without these, Closure Compiler renames them under :advanced optimization,
 * breaking reagent.dom.client/render and reagent.impl.batching/react-flush.
 * @externs
 */

ReactDOM.Root.render = function(children) {};
ReactDOM.flushSync = function(callback) {};
