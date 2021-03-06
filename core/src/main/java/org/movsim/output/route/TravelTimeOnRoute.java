/*
 * Copyright (C) 2010, 2011, 2012 by Arne Kesting, Martin Treiber, Ralph Germ, Martin Budden
 *                                   <movsim.org@gmail.com>
 * -----------------------------------------------------------------------------------------
 * 
 * This file is part of
 * 
 * MovSim - the multi-model open-source vehicular-traffic simulator.
 * 
 * MovSim is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * MovSim is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with MovSim. If not, see <http://www.gnu.org/licenses/>
 * or <http://www.movsim.org>.
 * 
 * -----------------------------------------------------------------------------------------
 */
package org.movsim.output.route;

import org.movsim.input.model.output.TravelTimeOnRouteInput;
import org.movsim.simulator.roadnetwork.RoadNetwork;
import org.movsim.simulator.roadnetwork.Route;
import org.movsim.utilities.ExponentialMovingAverage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TravelTimeOnRoute extends OutputOnRouteBase {

    /** The Constant logger. */
    final static Logger logger = LoggerFactory.getLogger(TravelTimeOnRoute.class);

    private final double tauEMA;

    private final double beta;

    private final FileTravelTimeOnRoute fileWriter;

    private double instantaneousTravelTime;

    private double totalTravelTime;

    private double meanSpeed;

    private double instTravelTimeEMA;
    
    private int numberOfVehicles;

    public TravelTimeOnRoute(double simulationTimestep, TravelTimeOnRouteInput input, RoadNetwork roadNetwork,
            Route route, boolean writeOutput) {
        super(roadNetwork, route);
        this.tauEMA = input.getTauEMA();
        this.beta = Math.exp(-simulationTimestep / tauEMA);
        fileWriter = writeOutput ? new FileTravelTimeOnRoute(input.getDt(), route) : null;
        totalTravelTime = 0;
    }

    @Override
    public void timeStep(double dt, double simulationTime, long iterationCount) {

        numberOfVehicles = roadNetwork.vehicleCount(route) - roadNetwork.obstacleCount(route);
        
        instantaneousTravelTime = roadNetwork.instantaneousTravelTime(route);

        totalTravelTime += instantaneousTravelTime;

        meanSpeed = route.getLength() / instantaneousTravelTime;

        instTravelTimeEMA = (simulationTime == 0) ? instantaneousTravelTime : ExponentialMovingAverage.calc(
                instantaneousTravelTime, instTravelTimeEMA, beta);

        if (fileWriter != null) {
            fileWriter.write(simulationTime, this);
        }
    }

    public double getInstantaneousTravelTime() {
        return instantaneousTravelTime;
    }

    public double getMeanSpeed() {
        return meanSpeed;
    }

    public double getInstantaneousTravelTimeEMA() {
        return instTravelTimeEMA;
    }

    public double getTotalTravelTime() {
        return totalTravelTime;
    }

    public int getNumberOfVehicles() {
        return numberOfVehicles;
    }

}
