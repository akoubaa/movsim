/**
 * Copyright (C) 2010, 2011 by Arne Kesting, Martin Treiber,
 *                             Ralph Germ, Martin Budden
 *                             <info@movsim.org>
 * ----------------------------------------------------------------------
 * 
 *  This file is part of 
 *  
 *  MovSim - the multi-model open-source vehicular-traffic simulator 
 *
 *  MovSim is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  MovSim is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with MovSim.  If not, see <http://www.gnu.org/licenses/> or
 *  <http://www.movsim.org>.
 *  
 * ----------------------------------------------------------------------
 */
package org.movsim.simulator.roadSection.impl;

import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;

import org.movsim.input.model.simulation.SimpleRampData;
import org.movsim.simulator.Constants;
import org.movsim.simulator.roadSection.InflowTimeSeries;
import org.movsim.simulator.roadSection.Onramp;
import org.movsim.simulator.vehicles.Vehicle;
import org.movsim.simulator.vehicles.VehicleContainer;
import org.movsim.simulator.vehicles.VehicleGenerator;
import org.movsim.utilities.impl.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: Auto-generated Javadoc
/**
 * The Class OnrampImpl.
 */
public class OnrampImpl implements Onramp {

    private static final String extensionFormat = ".S%d_log.csv";
    private static final String outputHeading = Constants.COMMENT_CHAR + 
    "     t[s], lane,  xEnter[m],    v[km/h],   qBC[1/h],  count,  queue\n";
    private static final String outputFormat = 
    	"%10.2f, %4d, %10.2f, %10.2f, %10.2f, %6d, %6d%n";

    /** The Constant logger. */
    final static Logger logger = LoggerFactory.getLogger(OnrampImpl.class);

    /** The Constant MINSPACE_MERGE_M. */
    final static double MINSPACE_MERGE_M = 2.0;

    /** The Constant RAMP_VEL_REDUCEFACTOR. */
    final static double RAMP_VEL_REDUCEFACTOR = 0.6;

    /** The veh generator. */
    private final VehicleGenerator vehGenerator;

    /** The main veh container. */
    private final VehicleContainer mainVehContainer;

    /** The inflow time series. */
    private final InflowTimeSeries inflowTimeSeries;

    /** The vehicle queue. */
    private final LinkedList<Vehicle> vehicleQueue;
    
    /** The x center position of the ramp. */
    private final double xCenter;
    
    /** The length of the ramp. */
    private final double length;

    /** The x up ramp marks the start of ther ramp. */
    private final double xUpRamp;
    
    /** The x down ramp marks the end of the ramp. */
    private final double xDownRamp;

    /** The n wait. */
    private double nWait;

    /** The fstr logging. */
    PrintWriter fstrLogging;

    /** The x enter last merge. status of last merging vehicle */
    private double xEnterLastMerge;
    
    /** The v enter last merge. */
    private double vEnterLastMerge;
    
    /** The lane enter last merge. */
    private int laneEnterLastMerge;
    
    /** The merge count. */
    private int mergeCount;

    /**
     * Instantiates a new onramp impl.
     * 
     * @param rampData
     *            the ramp data
     * @param vehGenerator
     *            the veh generator
     * @param mainVehContainer
     *            the main veh container
     * @param projectName
     *            the project name
     * @param rampIndex
     *            the ramp index
     */
    public OnrampImpl(SimpleRampData rampData, VehicleGenerator vehGenerator, VehicleContainer mainVehContainer,
            String projectName, int rampIndex) {

        this.vehGenerator = vehGenerator;
        vehicleQueue = new LinkedList<Vehicle>();
        this.mainVehContainer = mainVehContainer;

        if (rampData.withLogging()) {
            mergeCount = 0;
            final int roadCount = 1; // assuming only one road in the scenario for the moment
            final String filename = projectName + String.format(extensionFormat, rampIndex + roadCount);
            fstrLogging = FileUtils.getWriter(filename);
            fstrLogging.printf(outputHeading);
        }

        nWait = 0;
        inflowTimeSeries = new InflowTimeSeriesImpl(rampData.getInflowTimeSeries());
        
        this.length = rampData.getRampLength();
        this.xCenter = rampData.getRampStartPosition() + 0.5 * length;
        
        xUpRamp = rampData.getRampStartPosition();
        xDownRamp = xUpRamp + length;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.movsim.simulator.roadSection.Onramp#update(int, double, double)
     */
    @Override
    public void update(int itime, double dt, double time) {

        final double qBC = inflowTimeSeries.getFlow(time);
        nWait += qBC * dt;

        if (nWait >= 1) {
            // add vehicle that wants to enter to queue
            vehicleQueue.add(vehGenerator.createVehicle());
            nWait--;
            logger.debug("add vehicle to queue: onramp nWait = {}, queue.size() = {}.", nWait, vehicleQueue.size());
        }

        // tryMerge calculates position and index for the lane change
        // actual veh. length not dyn. relevant for this simulation -> lvehTest

        if (vehicleQueue.size() >= 1) {
            final boolean isMerging = tryMerge(vehicleQueue.getFirst(), mainVehContainer);
            if (isMerging) {
                vehicleQueue.removeFirst();
                if (fstrLogging != null) {
                    fstrLogging.printf(outputFormat, time, laneEnterLastMerge, xEnterLastMerge, 3.6 * vEnterLastMerge,
                    		3600 * qBC, mergeCount, vehicleQueue.size());
                    fstrLogging.flush();
                }
            }
        }

    }


    /**
     * Adds the vehicle from ramp.
     * 
     * @param vehToEnter
     *            the veh to enter
     * @param xEnter
     *            the x enter
     * @param vEnter
     *            the v enter
     * @param laneEnter
     *            the lane enter
     */
    private void addVehicleFromRamp(Vehicle vehToEnter, double xEnter, double vEnter, int laneEnter) {
        mainVehContainer.addFromRamp(vehToEnter, xEnter, vEnter, laneEnter);
        // update status for last merge
        mergeCount++;
        xEnterLastMerge = xEnter;
        vEnterLastMerge = vEnter;
        laneEnterLastMerge = laneEnter;
    }

    /**
     * Speed to enter.
     * 
     * @param speedLeader
     *            the speed leader
     * @return the double
     */
    private double speedToEnter(double speedLeader) {
        final double vcrit = 10; 
        final double reducefact = 1. - (1 - RAMP_VEL_REDUCEFACTOR) * Math.min(1., speedLeader / vcrit);
        return reducefact * speedLeader;
    }

    /**
     * Find down index within ramp.
     * 
     * @param mainVehicles
     *            the main vehicles
     * @return the int
     */
    private int findDownIndexWithinRamp(List<Vehicle> mainVehicles) {
        // for search in upstream direction increase index
        if (mainVehicles.isEmpty()) {
            logger.error("cannot operate on empty list !");
            System.exit(-1);
        }
        for (int i = 0, N = mainVehicles.size(); i < N; i++) {
            final double pos = mainVehicles.get(i).position();
            if (pos <= xDownRamp && pos >= xUpRamp)
                return i; // first vehicle in down direction
        }
        return -1;
    }

    /**
     * Find up index within ramp.
     * 
     * @param mainVehicles
     *            the main vehicles
     * @return the int
     */
    private int findUpIndexWithinRamp(List<Vehicle> mainVehicles) {
        // for search in downstream direction DECREASE index
        if (mainVehicles.isEmpty()) {
            logger.error("cannot operate on empty list !");
            System.exit(-1);
        }
        final int N = mainVehicles.size();
        for (int i = N - 1; i > -0; i--) {
            final double pos = mainVehicles.get(i).position();
            if (pos <= xDownRamp && pos >= xUpRamp)
                return i; // first vehicle in down direction
        }
        return -1;
    }

    /**
     * Try merge.
     * 
     * @param vehToEnter
     *            the veh to enter
     * @param mainVehContainer
     *            the main veh container
     * @return true, if successful
     */
    private boolean tryMerge(Vehicle vehToEnter, VehicleContainer mainVehContainer) {

        logger.debug("");
        final double xUp = xUpRamp;
        final double xDown = xUpRamp +  length;

        final List<Vehicle> mainVehicles = mainVehContainer.getVehicles();

        final int laneEnter = Constants.MOST_RIGHT_LANE; // single-lane simulation

        final int mainVehSize = mainVehicles.size();
        if (mainVehSize == 0) {
            logger.debug("empty road: merge anyway. mainVeh.size() = {}", mainVehSize);
            final double xEnter = xCenter;
            final double vEnter = speedToEnter(vehToEnter.getDesiredSpeedParameter()); // no leader
            addVehicleFromRamp(vehToEnter, xEnter, vEnter, laneEnter);
            return true;
        } else if (mainVehContainer.getMostDownstream().position() <= xCenter) {
            // most downstream mainroad vehicle is upstream of onramp
            final Vehicle mainVehDown = mainVehContainer.getMostDownstream();
            final double xEnter = Math.max(xCenter, mainVehDown.position() + 0.5 * length);
            final double vEnter = speedToEnter(vehToEnter.getDesiredSpeedParameter()); // no
                                                                                       // leader
            logger.debug("most downstream veh is still upstream of ramp center. mainVeh.size() = {}. posMostDown = {}",
                    mainVehSize, mainVehDown.position());
            addVehicleFromRamp(vehToEnter, xEnter, vEnter, laneEnter);
            return true;
        } else if (mainVehContainer.getMostUpstream().position() >= xCenter) {
            // most upstream mainroad vehicle has already passed onramp
            final Vehicle mainVehUp = mainVehContainer.getMostUpstream();
            final double xEnter = Math.min(xCenter, mainVehUp.position() - 0.5 * length);
            final double vEnter = speedToEnter(mainVehUp.speed());
            logger.debug("most upstream veh is already downstream of ramp center. mainVeh.size() = {}, posMostUp = {}",
                    mainVehSize, mainVehUp.position());
            addVehicleFromRamp(vehToEnter, xEnter, vEnter, laneEnter);
            return true;
        } else {
            logger.debug("okay, try merge mainVeh.size() = {}", mainVehSize);

            // logger.debug("mainVehContainer.getMostDownstream().position() = {} ",
            // mainVehContainer.getMostDownstream().position());
            // logger.debug("mainVehContainer.getMostUpstream().position() = {} ",
            // mainVehContainer.getMostUpstream().position());

            final int indexUp = findUpIndexWithinRamp(mainVehicles);
            final int indexDown = findDownIndexWithinRamp(mainVehicles);
            logger.debug("indexUp = {}, indexDown = {}", indexUp, indexDown);

            // scan through vehicle list on mainroad in upstream direction
            // (increase index)

            if (indexUp == -1 && indexDown == -1) {
                for (int i = 0; i < mainVehSize; i++) {
                    logger.debug("i = {}, position() = {} ", i, mainVehicles.get(i).position());
                }
                // enter in center
                final double xEnter = xCenter;
                final double vEnter = speedToEnter(vehToEnter.getDesiredSpeedParameter());
                addVehicleFromRamp(vehToEnter, xEnter, vEnter, laneEnter);
                return true;
            }

            for (int i = indexDown; i <= indexUp; i++) {
                logger.debug("i = {}, position() = {} ", i, mainVehicles.get(i).position());
            }
            double xEnter = -1;
            double vEnter = -1;

            double minGap = -1; // init

            // compare
            for (int i = indexDown; i <= Math.min(indexUp + 1, mainVehSize - 1); i++) {
                final Vehicle actualVeh = mainVehicles.get(i);
                final double posFront = (i > 0) ? mainVehicles.get(i - 1).position() : Double.MAX_VALUE; // abfrage
                final double xEnterTest = Math.min(Math.max(xUp, 0.5 * (actualVeh.position() + posFront)), xDown);
                double netGap = xEnterTest - actualVeh.position() - 0.5 * (actualVeh.length() + vehToEnter.length());
                if (i == indexUp + 1) {
                    netGap = mainVehicles.get(i - 1).position() - xEnterTest - 0.5
                            * (mainVehicles.get(i - 1).length() + vehToEnter.length());
                }

                logger.debug("netGap = {}, xEnterTest = {}", netGap, xEnterTest);
                if (netGap > MINSPACE_MERGE_M && netGap > minGap) {
                    // new !!
                    minGap = netGap;
                    xEnter = xEnterTest;
                    vEnter = (i > 0) ? speedToEnter(mainVehicles.get(i - 1).speed()) : speedToEnter(vehToEnter
                            .getDesiredSpeedParameter());
                }
            }
            // check between indexUp+1 and indexUp
            if (indexUp == mainVehSize) {
                // compare directly. assume position at 0
                final double posFront = mainVehicles.get(indexUp).position();
                final double xEnterTest = Math.min(Math.max(xUp, 0.5 * (0 + posFront)), xDown);
                final double netGap = posFront - xEnterTest - vehToEnter.length();
                logger.debug("test gap for most upstream  without back veh: netGap = {}, xEnterTest = {}", netGap,
                        xEnterTest);
                if (netGap > MINSPACE_MERGE_M && netGap > minGap) {
                    minGap = netGap;
                    xEnter = xEnterTest;
                    vEnter = speedToEnter(mainVehicles.get(indexUp).speed());
                }
            }

            if (xEnter > 0) {
                logger.debug("enter from ramp. squeeze in at x = {} with speed = {}", xEnter, vEnter);
                addVehicleFromRamp(vehToEnter, xEnter, vEnter, laneEnter);
                return true;
            }
        }
        return false;
    } 

}