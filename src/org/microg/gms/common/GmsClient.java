/*
 * Copyright 2014-2015 µg Project Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.microg.gms.common;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.internal.IGmsCallbacks;
import com.google.android.gms.common.internal.IGmsServiceBroker;

import org.microg.gms.common.api.ApiConnection;

public abstract class GmsClient<I extends IInterface> implements ApiConnection {
    private static final String TAG = "GmsClient";

    private final Context context;
    protected final GoogleApiClient.ConnectionCallbacks callbacks;
    protected final GoogleApiClient.OnConnectionFailedListener connectionFailedListener;
    protected ConnectionState state = ConnectionState.NOT_CONNECTED;
    private ServiceConnection serviceConnection;
    private I serviceInterface;

    public GmsClient(Context context, GoogleApiClient.ConnectionCallbacks callbacks,
            GoogleApiClient.OnConnectionFailedListener connectionFailedListener) {
        this.context = context;
        this.callbacks = callbacks;
        this.connectionFailedListener = connectionFailedListener;
    }

    protected abstract String getActionString();

    protected abstract void onConnectedToBroker(IGmsServiceBroker broker, GmsCallbacks callbacks)
            throws RemoteException;

    protected abstract I interfaceFromBinder(IBinder binder);

    @Override
    public void connect() {
        Log.d(TAG, "connect()");
        if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING) return;
        state = ConnectionState.CONNECTING;
        if (serviceConnection != null) {
            MultiConnectionKeeper.getInstance(context)
                    .unbind(getActionString(), serviceConnection);
        }
        serviceConnection = new GmsServiceConnection();
        if (!MultiConnectionKeeper.getInstance(context).bind(getActionString(),
                serviceConnection)) {
            state = ConnectionState.ERROR;
            handleConnectionFailed();
        }
    }

    public void handleConnectionFailed() {
        connectionFailedListener.onConnectionFailed(new ConnectionResult(ConnectionResult
                .API_UNAVAILABLE, null));
    }

    @Override
    public void disconnect() {
        Log.d(TAG, "disconnect()");
        if (state == ConnectionState.DISCONNECTING) return;
        if (state == ConnectionState.CONNECTING) {
            state = ConnectionState.DISCONNECTING;
            return;
        }
        serviceInterface = null;
        if (serviceConnection != null) {
            MultiConnectionKeeper.getInstance(context).unbind(getActionString(), serviceConnection);
            serviceConnection = null;
        }
        state = ConnectionState.NOT_CONNECTED;
    }

    @Override
    public boolean isConnected() {
        return state == ConnectionState.CONNECTED || state == ConnectionState.PSEUDO_CONNECTED;
    }

    @Override
    public boolean isConnecting() {
        return state == ConnectionState.CONNECTING;
    }

    public boolean hasError() {
        return state == ConnectionState.ERROR;
    }

    public Context getContext() {
        return context;
    }

    public I getServiceInterface() {
        return serviceInterface;
    }

    protected enum ConnectionState {
        NOT_CONNECTED, CONNECTING, CONNECTED, DISCONNECTING, ERROR, PSEUDO_CONNECTED
    }

    private class GmsServiceConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            try {
                Log.d(TAG, "ServiceConnection : onServiceConnected(" + componentName + ")");
                onConnectedToBroker(IGmsServiceBroker.Stub.asInterface(iBinder),
                        new GmsCallbacks());
            } catch (RemoteException e) {
                disconnect();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            state = ConnectionState.NOT_CONNECTED;
        }
    }

    public class GmsCallbacks extends IGmsCallbacks.Stub {

        @Override
        public void onPostInitComplete(int statusCode, IBinder binder, Bundle params)
                throws RemoteException {
            if (state == ConnectionState.DISCONNECTING) {
                state = ConnectionState.CONNECTED;
                disconnect();
                return;
            }
            state = ConnectionState.CONNECTED;
            serviceInterface = interfaceFromBinder(binder);
            Log.d(TAG, "GmsCallbacks : onPostInitComplete(" + serviceInterface + ")");
            callbacks.onConnected(params);
        }
    }

}
