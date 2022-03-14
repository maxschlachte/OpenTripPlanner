package org.opentripplanner.netex.mapping.support;


import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.opentripplanner.model.Station;
import org.opentripplanner.model.StopTime;
import org.opentripplanner.netex.index.api.NetexEntityIndexReadOnlyView;
import org.opentripplanner.netex.index.api.ReadOnlyHierarchicalMapById;
import org.rutebanken.netex.model.DatedServiceJourney;

import java.util.HashMap;
import java.util.Map;


/**
 * The responsibility of this class is to cache and index NeTEx entities uses be more than one mapper.
 * It supports the same hierarchical as the li
 * <p>
 * The typical use case is that tha same index is used in several mappers, so to avoid repeating the
 * same logic and create same index twice, we instead cache it here.
 * <p>
 * Only local current level entities are cashed, and the cache is thrown away for each set of files loaded
 * (shared files, shared group files and group files).
 *
 */
public class NetexMapperIndexes {

    private NetexMapperIndexes parent;

    private final Multimap<String, Station> stationsByMultiModalStationRfs;

    private final Map<String, StopTime> stopTimesByNetexId;

    private final Multimap<String, DatedServiceJourney> datedServiceJourneysBySjId;


    public NetexMapperIndexes(NetexEntityIndexReadOnlyView index, NetexMapperIndexes parent) {
        this.parent = parent;

        if(parent == null) {
            this.datedServiceJourneysBySjId = indexDSJBySJId(index.getDatedServiceJourneys());
            this.stationsByMultiModalStationRfs = ArrayListMultimap.create();
            this.stopTimesByNetexId = new HashMap<>();
        }
        else {
            // Cashed by level(shared files, shared group files and group files). If any entries exist at the current
            // level, then they will hide entries at a higher level.
            this.datedServiceJourneysBySjId =  index.getDatedServiceJourneys().localKeys().isEmpty()
                    ? parent.datedServiceJourneysBySjId
                    : indexDSJBySJId(index.getDatedServiceJourneys());


            // Feed global instances. These fields contain mapping from a netex id to a OTP domain
            // model object, hence we are not adding a lot of data to memory - only the id to object
            // mapping.
            this.stationsByMultiModalStationRfs = parent.stationsByMultiModalStationRfs;
            this.stopTimesByNetexId = parent.stopTimesByNetexId;
        }
    }

    public NetexMapperIndexes getParent() {
        return parent;
    }

    public Multimap<String, Station> getStationsByMultiModalStationRfs() {
        return stationsByMultiModalStationRfs;
    }

    public void addStationByMultiModalStationRfs(Multimap<String, Station> stationByMultiModalStationRfs) {
        this.stationsByMultiModalStationRfs.putAll(stationByMultiModalStationRfs);
    }

    /**
     * This is needed to assign a notice to a stop time. It is not part of the target
     * OTPTransitService, so we need to temporally cash this here.
     */
    public Map<String, StopTime> getStopTimesByNetexId() {
        return stopTimesByNetexId;
    }

    public void addStopTimesByNetexId(Map<String, StopTime> stopTimesByNetexId) {
        this.stopTimesByNetexId.putAll(stopTimesByNetexId);
    }

    public Multimap<String, DatedServiceJourney> getDatedServiceJourneysBySjId() {
        return datedServiceJourneysBySjId;
    }


    /* utility methods */

    static Multimap<String, DatedServiceJourney> indexDSJBySJId(
            ReadOnlyHierarchicalMapById<DatedServiceJourney> datedServiceJourneys
    ) {
        Multimap<String, DatedServiceJourney> dsjBySJId = ArrayListMultimap.create();
        for (DatedServiceJourney dsj : datedServiceJourneys.localValues()) {
            // The validation step ensure no NPE occurs here
            String sjId = dsj.getJourneyRef().get(0).getValue().getRef();
            dsjBySJId.put(sjId, dsj);
        }
        return dsjBySJId;
    }
}
