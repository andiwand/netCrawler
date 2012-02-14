package at.netcrawler.network.crawler;

import java.io.IOException;

import at.netcrawler.network.topology.Topology;


public abstract class NetworkCrawler {
	
	public abstract void crawl(Topology topology) throws IOException;
	
}