// Copyright 2015 Sebastian Kuerten
//
// This file is part of osm4j.
//
// osm4j is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// osm4j is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with osm4j. If not, see <http://www.gnu.org/licenses/>.
//
//
// This files is based on a file from Osmosis. The original file contained this
// copyright notice:
//
// This software is released into the Public Domain. See copying.txt for details.
//
//
// And the mentioned copying.txt states:
//
// Osmosis is placed into the public domain and where this is not legally
// possible everybody is granted a perpetual, irrevocable license to use
// this work for any purpose whatsoever.
//
// DISCLAIMERS
// By making Osmosis publicly available, it is hoped that users will find the
// software useful. However:
//   * Osmosis comes without any warranty, to the extent permitted by
//     applicable law.
//   * Unless required by applicable law, no liability will be accepted by
// the authors and distributors of this software for any damages caused
// as a result of its use.

package de.topobyte.osm4j.pbf.seq;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.slimjars.dist.gnu.trove.list.array.TLongArrayList;

import de.topobyte.osm4j.core.access.OsmHandler;
import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmMetadata;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.model.iface.OsmTag;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.core.model.impl.Metadata;
import de.topobyte.osm4j.core.model.impl.Node;
import de.topobyte.osm4j.core.model.impl.Relation;
import de.topobyte.osm4j.core.model.impl.RelationMember;
import de.topobyte.osm4j.core.model.impl.Tag;
import de.topobyte.osm4j.core.model.impl.Way;
import de.topobyte.osm4j.pbf.protobuf.Osmformat;

public class PrimParser
{

	private int granularity;
	private long latOffset;
	private long lonOffset;
	private int dateGranularity;
	private String[] strings;

	private boolean fetchMetadata;

	public PrimParser(Osmformat.PrimitiveBlock block, boolean fetchMetadata)
	{
		this.fetchMetadata = fetchMetadata;

		Osmformat.StringTable stringTable = block.getStringtable();
		strings = new String[stringTable.getSCount()];

		for (int i = 0; i < strings.length; i++) {
			strings[i] = stringTable.getS(i).toStringUtf8();
		}

		granularity = block.getGranularity();
		latOffset = block.getLatOffset();
		lonOffset = block.getLonOffset();
		dateGranularity = block.getDateGranularity();
	}

	/**
	 * Convert a latitude value stored in a protobuf into a double, compensating
	 * for granularity and latitude offset
	 */
	protected double parseLat(long degree)
	{
		// Support non-zero offsets. (We don't currently generate them)
		return (granularity * degree + latOffset) * .000000001;
	}

	/**
	 * Convert a longitude value stored in a protobuf into a double,
	 * compensating for granularity and longitude offset
	 */
	protected double parseLon(long degree)
	{
		// Support non-zero offsets. (We don't currently generate them)
		return (granularity * degree + lonOffset) * .000000001;
	}

	public long getTimestamp(Osmformat.Info info)
	{
		if (info.hasTimestamp()) {
			return dateGranularity * info.getTimestamp();
		}
		return -1;
	}

	public void parseNodes(List<Osmformat.Node> nodes, OsmHandler handler)
			throws IOException
	{
		for (Osmformat.Node n : nodes) {
			handler.handle(convert(n));
		}
	}

	public void parseWays(List<Osmformat.Way> ways, OsmHandler handler)
			throws IOException
	{
		for (Osmformat.Way w : ways) {
			handler.handle(convert(w));
		}
	}

	public void parseRelations(List<Osmformat.Relation> rels,
			OsmHandler handler) throws IOException
	{
		for (Osmformat.Relation r : rels) {
			handler.handle(convert(r));
		}
	}

	public OsmNode convert(Osmformat.Node n)
	{
		long id = n.getId();
		double lat = Double.NaN;
		double lon = Double.NaN;

		if (n.getLat() != Integer.MAX_VALUE) {
			lat = parseLat(n.getLat());
		}

		if (n.getLon() != Integer.MAX_VALUE) {
			lon = parseLon(n.getLon());
		}

		List<OsmTag> tags = new ArrayList<>();
		for (int j = 0; j < n.getKeysCount(); j++) {
			tags.add(new Tag(strings[n.getKeys(j)], strings[n.getVals(j)]));
		}

		OsmMetadata metadata = null;
		if (fetchMetadata && n.hasInfo()) {
			Osmformat.Info info = n.getInfo();
			metadata = convertMetadata(info);
		}

		return new Node(id, lon, lat, tags, metadata);
	}

	public OsmWay convert(Osmformat.Way w)
	{
		long id = w.getId();
		TLongArrayList nodes = new TLongArrayList();

		long lastId = 0;
		for (long j : w.getRefsList()) {
			nodes.add(j + lastId);
			lastId = j + lastId;
		}

		List<OsmTag> tags = new ArrayList<>();
		for (int j = 0; j < w.getKeysCount(); j++) {
			tags.add(new Tag(strings[w.getKeys(j)], strings[w.getVals(j)]));
		}

		OsmMetadata metadata = null;
		if (fetchMetadata && w.hasInfo()) {
			Osmformat.Info info = w.getInfo();
			metadata = convertMetadata(info);
		}

		return new Way(id, nodes, tags, metadata);
	}

	public OsmRelation convert(Osmformat.Relation r)
	{
		long id = r.getId();
		long lastMid = 0;

		List<OsmTag> tags = new ArrayList<>();
		for (int j = 0; j < r.getKeysCount(); j++) {
			tags.add(new Tag(strings[r.getKeys(j)], strings[r.getVals(j)]));
		}

		List<RelationMember> members = new ArrayList<>();
		for (int j = 0; j < r.getMemidsCount(); j++) {
			long mid = lastMid + r.getMemids(j);
			lastMid = mid;
			String role = strings[r.getRolesSid(j)];
			Osmformat.Relation.MemberType type = r.getTypes(j);

			EntityType t = getType(type);

			RelationMember member = new RelationMember(mid, t, role);
			members.add(member);
		}

		OsmMetadata metadata = null;
		if (fetchMetadata && r.hasInfo()) {
			Osmformat.Info info = r.getInfo();
			metadata = convertMetadata(info);
		}

		return new Relation(id, members, tags, metadata);
	}

	public OsmMetadata convertMetadata(Osmformat.Info info)
	{
		boolean visible = true;

		if (info.hasVisible() && !info.getVisible()) {
			visible = info.getVisible();
		}

		Metadata metadata = new Metadata(info.getVersion(), getTimestamp(info),
				info.getUid(), strings[info.getUserSid()], info.getChangeset(),
				visible);
		return metadata;
	}

	public EntityType getType(Osmformat.Relation.MemberType type)
	{
		switch (type) {
		default:
		case NODE:
			return EntityType.Node;
		case WAY:
			return EntityType.Way;
		case RELATION:
			return EntityType.Relation;
		}
	}

	public void parseDense(Osmformat.DenseNodes nodes, OsmHandler handler)
			throws IOException
	{
		Osmformat.DenseInfo denseInfo = null;
		boolean hasVisible = false;
		if (fetchMetadata && nodes.hasDenseinfo()) {
			denseInfo = nodes.getDenseinfo();
			hasVisible = denseInfo.getVisibleCount() != 0;
		}

		long id = 0, lat = 0, lon = 0;

		int version = 0, uid = 0, userSid = 0;
		long timestamp = 0, changeset = 0;

		int j = 0; // Index into the keysvals array.

		for (int i = 0; i < nodes.getIdCount(); i++) {
			id += nodes.getId(i);
			lat += nodes.getLat(i);
			lon += nodes.getLon(i);

			double latf = Double.NaN, lonf = Double.NaN;

			if (lat != Integer.MAX_VALUE) {
				latf = parseLat(lat);
			}

			if (lon != Integer.MAX_VALUE) {
				lonf = parseLon(lon);
			}

			List<OsmTag> tags = new ArrayList<>();

			OsmMetadata metadata = null;

			if (fetchMetadata && nodes.hasDenseinfo()) {
				version = denseInfo.getVersion(i);
				timestamp += denseInfo.getTimestamp(i);
				if (denseInfo.getUidCount() > 0) {
					uid += denseInfo.getUid(i);
				}
				if (denseInfo.getUserSidCount() > 0) {
					userSid += denseInfo.getUserSid(i);
				}
				if (denseInfo.getChangesetCount() > 0) {
					changeset += denseInfo.getChangeset(i);
				}
				boolean visible = true;
				if (hasVisible) {
					visible = denseInfo.getVisible(i);
				}
				metadata = new Metadata(version, timestamp * dateGranularity,
						uid, strings[userSid], changeset, visible);
			}

			// If empty, assume that nothing here has keys or vals.
			if (nodes.getKeysValsCount() > 0) {
				while (nodes.getKeysVals(j) != 0) {
					int keyid = nodes.getKeysVals(j++);
					int valid = nodes.getKeysVals(j++);
					tags.add(new Tag(strings[keyid], strings[valid]));
				}
				j++; // Skip over the '0' delimiter.
			}

			Node node = new Node(id, lonf, latf, tags, metadata);
			handler.handle(node);
		}
	}

	public List<OsmNode> convert(Osmformat.DenseNodes nodes)
	{
		List<OsmNode> results = new ArrayList<>(nodes.getIdCount());

		Osmformat.DenseInfo denseInfo = null;
		boolean hasVisible = false;
		if (fetchMetadata && nodes.hasDenseinfo()) {
			denseInfo = nodes.getDenseinfo();
			hasVisible = denseInfo.getVisibleCount() != 0;
		}

		long id = 0, lat = 0, lon = 0;

		int version = 0, uid = 0, userSid = 0;
		long timestamp = 0, changeset = 0;

		int j = 0; // Index into the keysvals array.

		for (int i = 0; i < nodes.getIdCount(); i++) {
			id += nodes.getId(i);
			lat += nodes.getLat(i);
			lon += nodes.getLon(i);

			double latf = Double.NaN, lonf = Double.NaN;

			if (lat != Integer.MAX_VALUE) {
				latf = parseLat(lat);
			}

			if (lon != Integer.MAX_VALUE) {
				lonf = parseLon(lon);
			}

			List<OsmTag> tags = new ArrayList<>();

			OsmMetadata metadata = null;

			if (fetchMetadata && nodes.hasDenseinfo()) {
				version = denseInfo.getVersion(i);
				timestamp += denseInfo.getTimestamp(i);
				if (denseInfo.getUidCount() > 0) {
					uid += denseInfo.getUid(i);
				}
				if (denseInfo.getUserSidCount() > 0) {
					userSid += denseInfo.getUserSid(i);
				}
				if (denseInfo.getChangesetCount() > 0) {
					changeset += denseInfo.getChangeset(i);
				}
				boolean visible = true;
				if (hasVisible) {
					visible = denseInfo.getVisible(i);
				}
				metadata = new Metadata(version, timestamp * dateGranularity,
						uid, strings[userSid], changeset, visible);
			}

			// If empty, assume that nothing here has keys or vals.
			if (nodes.getKeysValsCount() > 0) {
				while (nodes.getKeysVals(j) != 0) {
					int keyid = nodes.getKeysVals(j++);
					int valid = nodes.getKeysVals(j++);
					tags.add(new Tag(strings[keyid], strings[valid]));
				}
				j++; // Skip over the '0' delimiter.
			}

			results.add(new Node(id, lonf, latf, tags, metadata));
		}

		return results;
	}

}
