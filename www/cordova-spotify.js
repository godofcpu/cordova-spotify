const EventEmitter = require('./lib/EventEmitter.js');
const exec = require('./lib/execPromise.js');
const platform = require('./platforms');

class Session extends EventEmitter {
    constructor(sessionObject) {
        super();

        if (!sessionObject) {
            throw new Error("Missing native session object.");
        }

        Object.assign(this, sessionObject);
    }

    getMetadata() {
        return exec('getMetadata');
    }

    getPlaybackState() {
        return exec('getPlaybackState');
    }

    getPosition() {
        return exec('getPosition');
    }

    logout() {
        return exec('logout');
    }

    play(trackUri, indexInContext, position) {
        return exec('play', [trackUri, indexInContext, position]);
    }

    pause() {
        return exec('pause');
    }

    skipToNext() {
        return exec('skipToNext');
    }

    skipToPrevious() {
        return exec('skipToPrevious');
    }

    seekToPosition(positionInMs) {
        return exec('seekToPosition', [positionInMs]);
    }

    setShuffle(enabled) {
        return exec('setShuffle', [enabled]);
    }

    setRepeat(enabled) {
        return exec('setRepeat', [enabled]);
    }
}

function initSession(authData) {
    return (new Session(authData)).registerEvents()
        // Player is not ready to play when the SDK fires the callback.
        // Therefore we introduce some delay, so apps can start playing immediately
        // when the promise resolves.
        .then(session => new Promise(resolve => setTimeout(() => resolve(session), 2000)));
}

exports.authenticate = function (options) {
    return platform.authenticate(options)
        .then(initSession)
};

exports.login = function (options) {
    return platform.login(options)
        .then(authData => authData ? initSession(authData) : null);
};

exports.logout = function (options) {
    return platform.login(options);
};