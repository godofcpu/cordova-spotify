package rocks.festify;

import android.util.Log;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.text.SimpleDateFormat;

import android.app.Activity;
import android.content.Intent;


import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Error;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlaybackState;
import com.spotify.sdk.android.player.Metadata;
import com.spotify.sdk.android.player.Spotify;
import com.spotify.sdk.android.player.SpotifyPlayer;

import rocks.festify.ConnectionEventsHandler;
import rocks.festify.PlayerEventsHandler;
//import rocks.festify.AudioDeliveredHandler;

public class CordovaSpotify extends CordovaPlugin {
  private static final int LOGIN_REQUEST_CODE = 8139;
  private static final String TAG = "CordovaSpotify";

  private CallbackContext loginCallbackContext = null;
  private SpotifyPlayer player = null;

  private ConnectionEventsHandler connectionEventsHandler = new ConnectionEventsHandler();
  private PlayerEventsHandler playerEventsHandler = new PlayerEventsHandler();
  //private AudioDeliveredHandler audioDeliveredHandler = new AudioDeliveredHandler();

  @Override
  public boolean execute(String action, JSONArray args, CallbackContext callbackContext)
    throws JSONException {
    if ("logout".equals(action)) {

          String redirectUrl = args.getString(0);
          String clientId = args.getString(1);
          this.logout(callbackContext,clientId, redirectUrl);
          return true;
        }else if ("authenticate".equals(action)) {
      String redirectUrl = args.getString(0);
      String clientId = args.getString(1);
      JSONArray scopes = args.getJSONArray(2);
      boolean showDialog = args.getBoolean(3);
      this.authenticate(callbackContext, clientId, redirectUrl, scopes, showDialog);
      return true;
    } else if ("initSession".equals(action)) {
      String clientId = args.getString(0);
      String accessToken = args.getString(1);
      this.initSession(callbackContext, clientId, accessToken);
      return true;
    } else if ("getPosition".equals(action)) {
      this.getPosition(callbackContext);
      return true;
    } else if ("getMetadata".equals(action)) {
      this.getMetadata(callbackContext);
      return true;
    } else if ("getPlaybackState".equals(action)) {
      this.getPlaybackState(callbackContext);
      return true;
    } else if ("play".equals(action)) {
      if (!args.isNull(2)) {
        String trackUri = args.getString(0);
        this.play(callbackContext, trackUri, args.getInt(1), args.getInt(2));
      } else if (!args.isNull(1)) {
        String trackUri = args.getString(0);
        this.play(callbackContext, trackUri, args.getInt(1));
      } else if (!args.isNull(0)) {
        String trackUri = args.getString(0);
        this.play(callbackContext, trackUri);
      } else {
        this.play(callbackContext);
      }
      return true;
    } else if ("pause".equals(action)) {
      this.pause(callbackContext);
      return true;
    } else if ("skipToNext".equals(action)) {
      this.skipToNext(callbackContext);
      return true;
    } else if ("skipToPrevious".equals(action)) {
      this.skipToPrevious(callbackContext);
      return true;
    } else if ("seekToPosition".equals(action)) {
      int positionInMs = args.getInt(0);
      this.seekToPosition(callbackContext, positionInMs);
      return true;
    } else if ("setShuffle".equals(action)) {
      boolean enabled = args.getBoolean(0);
      this.setShuffle(callbackContext, enabled);
      return true;
    } else if ("setRepeat".equals(action)) {
      boolean enabled = args.getBoolean(0);
      this.setRepeat(callbackContext, enabled);
      return true;
    } else if ("registerEventsListener".equals(action)) {
      this.registerEventsListener(callbackContext);
      return true;
    } else {
      return false;
    }
  }

    /*
     * API Functions
     */

  private void authenticate(CallbackContext callbackContext, String clientId, String redirectUrl, JSONArray jsonScopes, boolean showDialog) {
    AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(
      clientId,
      AuthenticationResponse.Type.CODE,
      redirectUrl
    );
    String[] scopes = new String[jsonScopes.length()];
    for (int i = 0; i < jsonScopes.length(); i++) {
      try {
        scopes[i] = jsonScopes.getString(i);
      } catch (JSONException e) {
        callbackContext.error("OAuth scopes array could not be parsed.");
        return;
      }
    }
    builder.setScopes(scopes);
    builder.setShowDialog(true);

    this.loginCallbackContext = callbackContext;

    cordova.setActivityResultCallback(this);
    AuthenticationClient.openLoginActivity(this.cordova.getActivity(), LOGIN_REQUEST_CODE, builder.build());
  }


private void logout(CallbackContext callbackContext, String clientId, String redirectUrl) {
    AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(
      clientId,
      AuthenticationResponse.Type.CODE,
      redirectUrl
    );
    AuthenticationClient.openLoginInBrowser(this.cordova.getActivity(), builder.build());
  }



  private void initSession(final CallbackContext callbackContext, String clientId, String accessToken) {
    if (clientId == null || clientId.length() < 1) {
      callbackContext.error("Invalid clientId. Got an empty string.");
      return;
    }

    Config playerConfig = new Config(
      this.cordova.getActivity().getApplicationContext(),
      accessToken,
      clientId
    );

    Spotify.getPlayer(playerConfig, this.cordova.getActivity(), new SpotifyPlayer.InitializationObserver() {
      @Override
      public void onInitialized(SpotifyPlayer spotifyPlayer) {
        CordovaSpotify.this.player = spotifyPlayer;
        spotifyPlayer.addConnectionStateCallback(CordovaSpotify.this.connectionEventsHandler);
        spotifyPlayer.addNotificationCallback(CordovaSpotify.this.playerEventsHandler);

        callbackContext.success("Success");
      }

      @Override
      public void onError(Throwable throwable) {
        callbackContext.error("Could not initialize player: " + throwable.toString());
      }
    });
  }

  private void getPosition(final CallbackContext callbackContext) {
    SpotifyPlayer player = this.player;
    if (player == null) {
      callbackContext.error("Invalid player. Please call initSession first!");
      return;
    }

    PlaybackState state = player.getPlaybackState();
    if (state == null) {
      callbackContext.error("Received null from SpotifyPlayer.getPlaybackState()!");
      return;
    }

    PluginResult res = new PluginResult(PluginResult.Status.OK, (float) state.positionMs);
    callbackContext.sendPluginResult(res);
  }


  private JSONObject trackToJSON(Metadata.Track track) {
    JSONObject obj = new JSONObject();

    try {
      obj.put("name", track.name);
      obj.put("url", track.uri);
      obj.put("artistName", track.artistName);
      obj.put("artistUri", track.artistUri);
      obj.put("albumName", track.albumName);
      obj.put("albumUri", track.albumUri);
      obj.put("durationMs", track.durationMs);
      obj.put("indexInContext", track.indexInContext);
      obj.put("albumCoverWebUrl", track.albumCoverWebUrl);
    } catch (JSONException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    return obj;
  }

  private void getMetadata(final CallbackContext callbackContext) {
    SpotifyPlayer player = this.player;
    if (player == null) {
      callbackContext.error("Invalid player. Please call initSession first!");
      return;
    }

    Metadata state = player.getMetadata();
    if (state == null) {
      callbackContext.error("Received null from SpotifyPlayer.Metadata()!");
      return;
    }


    JSONObject obj = new JSONObject();

    try {
      obj.put("contextUri", state.contextUri);
      obj.put("contextName", state.contextName);

      if (state.prevTrack != null) {
        obj.put("prevTrack", this.trackToJSON(state.prevTrack));
      }
      if (state.currentTrack != null) {
        obj.put("currentTrack", this.trackToJSON(state.currentTrack));
      }
      if (state.nextTrack != null) {
        obj.put("nextTrack", this.trackToJSON(state.nextTrack));
      }

    } catch (JSONException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }


    PluginResult res = new PluginResult(PluginResult.Status.OK, obj);
    callbackContext.sendPluginResult(res);
  }


  private void getPlaybackState(final CallbackContext callbackContext) {
    SpotifyPlayer player = this.player;
    if (player == null) {
      callbackContext.error("Invalid player. Please call initSession first!");
      return;
    }

    PlaybackState state = player.getPlaybackState();
    if (state == null) {
      callbackContext.error("Received null from SpotifyPlayer.Metadata()!");
      return;
    }


    JSONObject obj = new JSONObject();

    try {
      obj.put("isPlaying", state.isPlaying);
      obj.put("isRepeating", state.isRepeating);
      obj.put("isActiveDevice", state.isActiveDevice);
      obj.put("isShuffling", state.isShuffling);
      obj.put("positionMs", state.positionMs);
    } catch (JSONException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    PluginResult res = new PluginResult(PluginResult.Status.OK, obj);
    callbackContext.sendPluginResult(res);
  }


  private void play(final CallbackContext callbackContext) {
    SpotifyPlayer player = this.player;
    if (player == null) {
      callbackContext.error("Invalid player. Please call initSession first!");
      return;
    }

    // resume
    player.resume(new Player.OperationCallback() {
      @Override
      public void onSuccess() {
        callbackContext.success("Success");
      }

      @Override
      public void onError(Error error) {
        callbackContext.error("Resume failed: " + error.toString());
      }
    });
  }

  private void skipToNext(final CallbackContext callbackContext) {
    SpotifyPlayer player = this.player;
    if (player == null) {
      callbackContext.error("Invalid player. Please call initSession first!");
      return;
    }

    // resume
    player.skipToNext(new Player.OperationCallback() {
      @Override
      public void onSuccess() {
        callbackContext.success("Success");
      }

      @Override
      public void onError(Error error) {
        callbackContext.error("skipToNext failed: " + error.toString());
      }
    });
  }


  private void skipToPrevious(final CallbackContext callbackContext) {
    SpotifyPlayer player = this.player;
    if (player == null) {
      callbackContext.error("Invalid player. Please call initSession first!");
      return;
    }

    // resume
    player.skipToPrevious(new Player.OperationCallback() {
      @Override
      public void onSuccess() {
        callbackContext.success("Success");
      }

      @Override
      public void onError(Error error) {
        callbackContext.error("skipToPrevious failed: " + error.toString());
      }
    });
  }


  private void seekToPosition(final CallbackContext callbackContext, int positionInMs) {
    SpotifyPlayer player = this.player;
    if (player == null) {
      callbackContext.error("Invalid player. Please call initSession first!");
      return;
    }

    // resume
    player.seekToPosition(new Player.OperationCallback() {
      @Override
      public void onSuccess() {
        callbackContext.success("Success");
      }

      @Override
      public void onError(Error error) {
        callbackContext.error("seekToPosition failed: " + error.toString());
      }
    }, positionInMs);
  }


  private void setShuffle(final CallbackContext callbackContext, boolean enabled) {
    SpotifyPlayer player = this.player;
    if (player == null) {
      callbackContext.error("Invalid player. Please call initSession first!");
      return;
    }

    // resume
    player.setShuffle(new Player.OperationCallback() {
      @Override
      public void onSuccess() {
        callbackContext.success("Success");
      }

      @Override
      public void onError(Error error) {
        callbackContext.error("setShuffle failed: " + error.toString());
      }
    }, enabled);
  }


  private void setRepeat(final CallbackContext callbackContext, boolean enabled) {
    SpotifyPlayer player = this.player;
    if (player == null) {
      callbackContext.error("Invalid player. Please call initSession first!");
      return;
    }

    // resume
    player.setRepeat(new Player.OperationCallback() {
      @Override
      public void onSuccess() {
        callbackContext.success("Success");
      }

      @Override
      public void onError(Error error) {
        callbackContext.error("setRepeat failed: " + error.toString());
      }
    }, enabled);
  }

  private void play(final CallbackContext callbackContext, String trackUri, int index, int positionInMs) {
    SpotifyPlayer player = this.player;
    if (player == null) {
      callbackContext.error("Invalid player. Please call initSession first!");
      return;
    }

    if (trackUri != null && trackUri.length() > 0) {
      player.playUri(new Player.OperationCallback() {
        @Override
        public void onSuccess() {
          callbackContext.success("Success");
        }

        @Override
        public void onError(Error error) {
          callbackContext.error("Playing of track failed: " + error.toString());
        }
      }, trackUri, index, positionInMs);
    } else {
      // resume
      this.play(callbackContext);
    }
  }

  private void play(final CallbackContext callbackContext, String trackUri, int index) {
    this.play(callbackContext, trackUri, index, 0);
  }


  private void play(final CallbackContext callbackContext, String trackUri) {

      this.play(callbackContext, trackUri, 0, 0);

  }

  private void pause(final CallbackContext callbackContext) {
    SpotifyPlayer player = this.player;
    if (player == null) {
      callbackContext.error("Invalid player. Please call initSession first!");
      return;
    }

    PlaybackState state = player.getPlaybackState();
    if (!state.isPlaying) {
      callbackContext.success("Not playing");
      return;
    }

    player.pause(new Player.OperationCallback() {
      @Override
      public void onSuccess() {
        callbackContext.success("Success");
      }

      @Override
      public void onError(Error error) {
        callbackContext.error("Pausing failed: " + error.toString());
      }
    });
  }

  private void registerEventsListener(final CallbackContext callbackContext) {
    this.connectionEventsHandler.setCallback(callbackContext);
    this.playerEventsHandler.setCallback(callbackContext);

    final PluginResult res = new PluginResult(PluginResult.Status.OK);
    res.setKeepCallback(true);
    callbackContext.sendPluginResult(res);
  }

    /*
     * CALLBACKS
     */

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    super.onActivityResult(requestCode, resultCode, intent);

    // Check if result comes from the correct activity
    if (requestCode == LOGIN_REQUEST_CODE) {
      this.onLoginResult(resultCode, intent);
    }
  }

  @Override
  public void onDestroy() {
    SpotifyPlayer player = this.player;
    this.player = null;
    if (player != null) {
      try {
        Spotify.awaitDestroyPlayer(this.cordova.getActivity(), 5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Log.wtf(TAG, "Interrupted while destroying Spotify player.", e);
      }
    }
  }

  private void onLoginResult(int resultCode, Intent intent) {
    final CallbackContext cb = this.loginCallbackContext;
    if (cb == null) {
      return;
    }
    this.loginCallbackContext = null;

    final AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
    if (response.getType() != AuthenticationResponse.Type.CODE) {
      cb.error("Wrong response type: " + response.getType().toString());
    } else {
      HashMap<String, String> responseMap = new HashMap();
      responseMap.put("code", response.getCode());
      cb.success(new JSONObject(responseMap));
    }
  }
}
