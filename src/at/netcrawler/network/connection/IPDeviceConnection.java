package at.netcrawler.network.connection;

import at.netcrawler.network.IPDeviceAccessor;


public abstract class IPDeviceConnection extends DeviceConnection {
	
	public IPDeviceConnection(IPDeviceAccessor accessor,
			ConnectionSettings settings) {
		super(accessor, settings);
	}
	
}