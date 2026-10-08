import {peakEnvelope} from './waveform.js';
onmessage=e=>postMessage(peakEnvelope(e.data));
