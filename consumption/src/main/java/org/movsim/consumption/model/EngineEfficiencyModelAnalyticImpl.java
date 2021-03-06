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
package org.movsim.consumption.model;

import org.movsim.consumption.input.xml.model.ConsumptionEngineModelInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EngineEfficiencyModelAnalyticImpl implements EngineEfficienyModel {

    /** The Constant logger. */
    final static Logger logger = LoggerFactory.getLogger(EngineEfficiencyModelAnalyticImpl.class);

    private double idleConsumptionRate; // idling consumption rate (Liter/s)

    public double maxPower; // max. effective mechanical engine power (W)

    private double cylinderVolume; // effective volume of the cylinders of the engine

    private double minEffPressure; // in Pascal, effective part of pe lost by gear and engine friction (N/m^2)

    private double maxEffPressure; // in Pascal

    private double idleMoment;

    private double maxMoment;

    public double cSpec0Idle;

    private double minSpecificConsumption; // (kg/Ws)

    private final EngineRotationModel engineRotationsModel;

    public EngineEfficiencyModelAnalyticImpl(ConsumptionEngineModelInput engineInput, EngineRotationModel engineRotationsModel) {

        this.engineRotationsModel = engineRotationsModel;

        initialize(engineInput);

    }

    private void initialize(ConsumptionEngineModelInput engineInput) {
        maxPower = engineInput.getMaxPower();

        idleConsumptionRate = engineInput.getIdleConsumptionRateLiterPerSecond();
        minSpecificConsumption = engineInput.getMinSpecificConsumption();

        cylinderVolume = engineInput.getCylinderVolume();

        minEffPressure = engineInput.getEffectivePressureMinimum();
        maxEffPressure = engineInput.getEffectivePressureMaximum();

        maxMoment = getMaxMoment();
        idleMoment = Moments.getModelLossMoment(engineRotationsModel.getIdleFrequency());

        double powerIdle = Moments.getLossPower(engineRotationsModel.getIdleFrequency());
        cSpec0Idle = idleConsumptionRate * ConsumptionConstants.RHO_FUEL_PER_LITER / powerIdle; // in kg/(Ws)

        // if (logger.isDebugEnabled()) {
        // logger.debug(String.format("powerIdle=%f W", getLossPower(getIdleFrequency())));
        // logger.debug(String.format("maxMoment=%f Nm", maxMoment));
        // logger.debug(String.format("idleMoment=%f Nm", idleMoment));
        //
        // logger.debug(String.format(
        // "cSpez0Idle=%f kg/kWh=%f l/kWh\n minimumSpecificConsumption=%e kg/Ws=%f kg/kWh \n"
        // + "cSpez0(fIdle,M=160Nm)=%f g/kWh", 3.6e6 * cSpec0Idle, 3.6e6 * cSpec0Idle
        // / ConsumptionConstants.RHO_FUEL_PER_LITER, minSpecificConsumption, 3.6e6 * minSpecificConsumption,
        // cSpecific0(getIdleFrequency(), 160, minSpecificConsumption) * 3.6e9));
        //
        // logger.debug(String.format("Test: dotC(f_idle,0)=%f l/h",
        // 3.6e6 * consRateAnalyticModel(getIdleFrequency(), 0)));
        //
        // logger.debug(String.format("dotC(0.5*fmax,0.5*Pmax)=%f l/h",
        // 3.6e6 * consRateAnalyticModel(0.5 * getMaxFrequency(), 0.5 * getMaxPower())));
        //
        // logger.debug(String.format("cSpecific(f=3000/min, M=160Nm)=%f g/kWh",
        // 3.6e9 * cSpecific0ForMechMoment(3000 / 60., 160)));
        // logger.debug(String.format("cSpecific(f=5000/min, M=200Nm)=%f g/kWh",
        // 3.6e9 * cSpecific0ForMechMoment(5000 / 60., 200)));
        // }

    }

    // consumption rate (m^3/s) as function of frequency and output power
    private double consRateAnalyticModel(double frequency, double mechPower) {
        final double indMoment = Moments.getMoment(mechPower, frequency) + Moments.getModelLossMoment(frequency);
        final double totalPower = mechPower + Moments.getLossPower(frequency);
        final double dotCInLiterPerSecond = 1. / ConsumptionConstants.RHO_FUEL_PER_LITER * totalPower
                * cSpecific0(frequency, indMoment, minSpecificConsumption);
        return Math.max(0, dotCInLiterPerSecond / 1000.);
    }

    // model output 1: specific consumption per power as function of moment
    public double cSpecific0ForMechMoment(double frequency, double mechMoment) {
        final double indMoment = mechMoment + Moments.getModelLossMoment(frequency);
        return (mechMoment <= 0 || mechMoment > maxMoment) ? 0 : cSpecific0(frequency, indMoment,
                minSpecificConsumption) * (indMoment / mechMoment);
    }

    // model output 2: consumption rate (liter/s) as function of power
    public double cSpecific0(double frequency, double indMoment, double minCSpec0) {
        return minCSpec0
                + (cSpec0Idle - minCSpec0)
                * (Math.exp(1 - indMoment / idleMoment) + Math.exp(1
                        - (maxMoment + Moments.getModelLossMoment(frequency) - indMoment) / idleMoment));
    }

    // --------------------------------------------------------

    @Override
    public double getFuelFlow(double frequency, double power) {
        return consRateAnalyticModel(frequency, power); // returns m^3/s !!
    }

    @Override
    public double getIdleConsumptionRate() {
        return idleConsumptionRate;
    }

    @Override
    public double getMaxPower() {
        return maxPower;
    }

    public double getMaxMoment() {
        return cylinderVolume * maxEffPressure / (4 * Math.PI);
    }


}
