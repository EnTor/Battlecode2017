package slugmacro;

import battlecode.common.*;

import static java.lang.Math.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.TreeMap;

public class RobotPlayer {
	static RobotController rc;
	static RobotType type;
	static Team enemyTeam = Team.B;
	static final float PI = (float) Math.PI;
	
	static final int BULLET_MARGIN = 10; // how many bullets that are required to be in the bank after a robot is queued up
	static final int ARCHON_TIMEOUT = 60;
//	static final Direction[] dirList = {new Direction(0), new Direction(PI / 4), new Direction(PI / 2), new Direction(3 * PI / 4), 
//			new Direction(PI), new Direction(-PI / 4), new Direction(-PI / 2), new Direction(-3 * PI / 4)};
	static Direction[] dirList = new Direction[30];
	static int rushingEnemies = 0;
	static boolean rushUnderControl = true;
	static final int UNIT_DONATION_LIMIT = 75;
	static final int orderChannel = 0;

	//Movement
	static float moveLength;
	static MapLocation currentLocation;
	static MapLocation targetLocation;
	static Direction targetDirection;
	static Direction casualDirection;
	
	//Scanning
	static RobotInfo[] enemyRobots;
	static RobotInfo[] friendlyRobots;
	static TreeInfo[] trees;
	static BulletInfo[] bullets;
	
	//Build Counts
	static final int INITIAL_BUILD_GARDENER_MAX_COUNT = 1;
	static final int FARM_GARDENER_MAX_COUNT = 4;
	static final int INITIAL_SCOUT_BUILD_COUNT = 0;
	static int initinal_lumberjack_count = 4;
	static boolean screwEarlyLumberjacksFFSWeHavePlentyOfRoom = false;
	static int lumberjacksUntilGardeners = 2;

	// Scout
	static int superTreeID = -1;
	static HashSet<TreeInfo> mappedTrees = new HashSet<>();
	
	//Gardener
	static boolean gardenerIsBuilder;
	static final int IDLE_GARDENERS = 2;
	static int tankNext = 0;
	static int wander = 0;
	
	//Farming
    static int taskStage = 0;
	static final Direction[] plantDirections = new Direction[] {
    		new Direction(0.33347317f), 
    		new Direction(1.38067072f), 
    		new Direction(2.42786827f), 
    		new Direction(3.47506582f), 
    		new Direction(4.52226337f), 
    		new Direction(5.56946092f)
    		};
    static final Direction[] farmDirections = new Direction[] {
    		new Direction(0.0f),
    		new Direction(1.04719755f),
    		new Direction(2.09439510f),
    		new Direction(3.14159265f),
    		new Direction(4.18879020f),
    		new Direction(5.23598775f)
    		};
    static final float farmerSpacing = 5.4f;	//5.32f for tightest possible
    static final float farmerOffset = 0;

	public static void run(RobotController rcIn) throws GameActionException {
		rc = rcIn;
		if (rc.getTeam() == Team.B) {
			enemyTeam = Team.A;
		}
		
		for(int i = 0; i < dirList.length; i++){
			dirList[i] = new Direction(2 * PI * i / 30);
		}

		currentLocation = rc.getLocation();
		casualDirection = currentLocation.directionTo(rc.getInitialArchonLocations(enemyTeam)[0]);
		targetLocation = rc.getInitialArchonLocations(enemyTeam)[0];
		targetDirection = currentLocation.directionTo(targetLocation);
		type = rc.getType();
		moveLength = type.strideRadius;
		
		
		switch (type) {
			case ARCHON:
				runArchon();
				break;
			case GARDENER:
				if(rc.readBroadcast(26) % 2 == 1){
					int gardenersInQueue = rc.readBroadcast(5) - 1;
					rc.broadcast(5, gardenersInQueue >= 0 ? gardenersInQueue : 0);
					gardenerIsBuilder = true;
				} else {
					targetLocation = new MapLocation(
			    			Math.round(rc.getLocation().x / (farmerSpacing * 2)) * farmerSpacing * 2 + farmerOffset,
			    			Math.round(rc.getLocation().y / (farmerSpacing * 2)) * farmerSpacing * 2 + farmerOffset
			    			);
					gardenerIsBuilder = false;
				}
				rc.broadcast(26, rc.readBroadcast(26) >>> 1);
				runGardener();
				break;
			case SOLDIER:
				runSoldier();
				break;
			case LUMBERJACK:
				runLumberjack();
				break;
			case SCOUT:
				runScout();
				break;
			case TANK:
				runTank();
				break;
		}
	}

	private static void runArchon() throws GameActionException {
		//Make out last archon
		boolean isInformer = false;
		int nrFormerArchons = rc.readBroadcast(6) + 1;
		rc.broadcast(6, nrFormerArchons);
		
		rc.broadcastBoolean(37, false);
		rc.broadcast(35, 0);
		int informerLives = -1;
		boolean informerSystemFailed = false;
		
		if(rc.getRoundNum() < 2){
			scanMapEdges();
			investigateStart();
			rc.broadcastFloat(996, -1f);
			rc.broadcastFloat(997, 10000f);
			rc.broadcastFloat(998, -1f);
			rc.broadcastFloat(999, 10000f);
		}
		
		
		while (true) {
			try {
				if(informerLives == rc.readBroadcast(35) && rc.getRoundNum() > 20){
					informerSystemFailed = true;
					rc.broadcastBoolean(37, true);
				}
				
				if(rc.getRoundNum() > 500){
					rc.broadcast(orderChannel, 0);
				}
				informerLives = rc.readBroadcast(35);	
				checkVictoryPoints();
				currentLocation = rc.getLocation();
				clearEnemyArchonChannels();
				scanMapEdges();
				
				enemyRobots = rc.senseNearbyRobots(-1, enemyTeam);
				iAmUnderAttack();

				if(rc.getTreeCount() >= 36 || rc.getRoundNum() > 700){
					rushingEnemies = 0;
					rushUnderControl = true;
					rc.broadcastInt(29, rushingEnemies);
				} else {
					if (rc.getRoundNum() % 50 == 0){
						rc.broadcast(29, 0);
					}
					detectRush();
					rushingEnemies = rc.readBroadcast(29);
					rc.broadcast(36, rc.readBroadcast(34));
					rushUnderControl = rushingEnemies <= rc.readBroadcast(34);
				}
				
				//intelligentMove(randomDirection());
				passiveMove();
				
				archonBuild(informerSystemFailed);
				rc.broadcast(4, rc.readBroadcast(3) + rc.readBroadcast(5));
				 
				//Need channel 6 to make out the final archon
				if (rc.getRoundNum() != 1 && nrFormerArchons == rc.readBroadcast(6)) {
					isInformer = true;
				}
				
				//Do this if this archon is the final archon
				if (isInformer) {
					rc.broadcast(35, rc.readBroadcast(35) + 1);
					rc.broadcast(3, 0);
					rc.broadcast(34, 0);
				}
	
				Clock.yield();
			} catch (GameActionException e) {
				System.out.println("Archon Exception");
				e.printStackTrace();
			}
		}
	}
	
	private static void investigateStart(){
		try {
			//Search if starting area holds lots of trees
			TreeInfo[] earlyTrees = rc.senseNearbyTrees(-1);
			int length = earlyTrees.length;
			rc.broadcast(9, min(max(rc.readBroadcast(9), length / 2), initinal_lumberjack_count));
				
			if(length < 2){
				screwEarlyLumberjacksFFSWeHavePlentyOfRoom = true;
			}
			lumberjacksUntilGardeners = min(length / 10, 1);
			
			//TODO Check if one is trapped or not
			rc.broadcast(50, 2); //Soldier -> scout -> farmers (default
			//else rc.broadcast(50, 1)  && Scout -> farmers
			//else rc.broadcast(50, 3)  && Lumberjack -> scout -> farmers
			
			//TODO: Check 3-4 vertically from own archon to enemyarchon if there exists path, Maybe also some kind of outer paths, THis is currently wrong
			/*RobotInfo[]	earlyRobots = rc.senseNearbyRobots(-1, enemyTeam);
			for(RobotInfo enemyRobot: earlyRobots){
				if(enemyRobot.getType() == type.ARCHON){
					MapLocation enemyLocation = enemyRobot.getLocation();
					Direction enemyDirection = currentLocation.directionTo(enemyLocation);
					float enemyDistance = currentLocation.distanceTo(enemyLocation);
					float checkRadius = enemyDistance - enemyRobot.getRadius() - type.bodyRadius - 0.1f;
					MapLocation half = currentLocation.add(enemyDirection, enemyDistance / 2);
					int occupiedCount = 0;
					MapLocation firstLocation = currentLocation.add(enemyDirection, i).add(enemyDirection.rotateRightRads(PI / 2), 4f);
					for(int i = (int) Math.floor(type.bodyRadius + 1.01f); i < (int) Math.floor(enemyDistance - enemyRobot.getRadius()) ; i += 2){
						
						for(int i = 0; i < 5; i++){
						
						rc.setIndicatorDot(firstLocation, 100, 255, 0);
						if(rc.isCircleOccupied(firstLocation, 1f)){	
							occupiedCount++;
						}
						MapLocation secondLocation = firstLocation.add(enemyDirection.rotateLeftRads(PI / 2), 2f);
						rc.setIndicatorDot(secondLocation, 100, 255, 255);
						if(rc.isCircleOccupied(secondLocation, 1f)){
							occupiedCount++;
						}
						MapLocation thirdLocation = firstLocation.add(enemyDirection.rotateRightRads(PI / 2), 2f);
						rc.setIndicatorDot(thirdLocation, 100, 100, 0);
						if(rc.isCircleOccupied(thirdLocation, 1f)){
							occupiedCount++;
						}
						MapLocation fourthLocation = secondLocation.add(enemyDirection.rotateLeftRads(PI / 2), 2f);
						rc.setIndicatorDot(fourthLocation, 100, 100, 0);
						if(rc.isCircleOccupied(fourthLocation, 1f)){
							occupiedCount++;
						}
						MapLocation fifthLocation = thirdLocation.add(enemyDirection.rotateRightRads(PI / 2), 2f);
						rc.setIndicatorDot(fifthLocation, 100, 255, 255);
						if(rc.isCircleOccupied(fifthLocation, 1f)){
							occupiedCount++;
						}
						
						System.out.println("occupied: " + occupiedCount);
						if(occupiedCount == 5){
							rc.broadcast(orderChannel, 0);
							break;
						} else {
							rc.broadcast(orderChannel, 1);
						}
						occupiedCount = 0;
					}
				}
				System.out.println(rc.readBroadcast(orderChannel));
			}*/
			rc.broadcast(orderChannel, 0);
		} catch (GameActionException e) {
			System.out.println("Archon Exception");
			e.printStackTrace();
		}
	}
	

	private static void archonBuild(boolean informerSystemFailed){
		try{
			//IMPROVISATION
			if(informerSystemFailed){
				if(rc.isBuildReady() && rc.getRoundNum() % 20 == 0){
					double rand = random();
					if (tryBuildRobot(RobotType.GARDENER)){
						if(rand < 0.5){
							rc.broadcast(5, rc.readBroadcast(5) + 1);
							rc.broadcast(26, (rc.readBroadcast(26) << 1) + 1);
						} else {
							rc.broadcast(27, rc.readBroadcast(27) + 1);
							rc.broadcast(26, rc.readBroadcast(26) << 1);
						}
					}
				}
				return;
			}
			
			//ACTUAL SYSTEM
			if (rc.isBuildReady() && (rc.readBroadcast(3) + rc.readBroadcast(5) < INITIAL_BUILD_GARDENER_MAX_COUNT + rc.getTreeCount() / 12 || rc.getTeamBullets() > 2000)) {
				if (tryBuildRobot(RobotType.GARDENER)){
					rc.broadcast(5, rc.readBroadcast(5) + 1);
					rc.broadcast(26, (rc.readBroadcast(26) << 1) + 1);
					return;
				}
			}
			
			//Farm gardeners
			if(rc.isBuildReady() && rc.readBroadcast(orderChannel) != 1 && rushUnderControl &&
					rc.readBroadcast(28) < IDLE_GARDENERS &&
					rc.readBroadcast(7) >= lumberjacksUntilGardeners && rc.readBroadcast(50) == 0 && rc.readBroadcast(33) >= rc.readBroadcast(27)){
				if(tryBuildRobot(RobotType.GARDENER)){
					rc.broadcast(27, rc.readBroadcast(27) + 1);
					rc.broadcast(26, rc.readBroadcast(26) << 1);
					rc.broadcast(28, rc.readBroadcast(28) + 1);
				}
			}
		} catch (GameActionException e) {
			System.out.println("Archon Exception");
			e.printStackTrace();
		}
	}
	
	private static void passiveMove(){
		try {
			float x_r = rc.readBroadcastFloat(998);
			float x_l = rc.readBroadcastFloat(999);
			float y_t =  rc.readBroadcastFloat(996);
			float y_b = rc.readBroadcastFloat(997);
			TreeInfo[] friendlyTrees = rc.senseNearbyTrees(4, rc.getTeam());
			if(x_r != -1f && currentLocation.x > x_r - 4) {
				intelligentMove(new Direction(PI));
			} else if (x_l != 10000f && currentLocation.x < x_l + 4) {
				intelligentMove(new Direction(0));
			} else if (y_t != -1f && currentLocation.y > y_t - 4) {
				intelligentMove(new Direction(-PI / 2));
			} else if (y_b != 10000f && currentLocation.y < y_b + 4){
				intelligentMove(new Direction(PI / 2));
    		} else if(friendlyTrees.length > 0){
				intelligentMove((currentLocation.directionTo(friendlyTrees[0].getLocation())).opposite());
			} else {
				intelligentMove(randomDirection());
			}
		} catch (GameActionException e) {
			System.out.println("Archon Exception");
			e.printStackTrace();
		}
	}

	private static void runGardener() throws GameActionException {
		boolean holdForVictoryPoints = false;
		while (true) {
			try {
				holdForVictoryPoints = checkVictoryPoints();
				currentLocation = rc.getLocation();
				clearEnemyArchonChannels();
				scanMapEdges();
				
				enemyRobots = rc.senseNearbyRobots(-1, enemyTeam);
				iAmUnderAttack();
				if(rc.getTreeCount() < 36){
					detectRush();
					rushingEnemies = rc.readBroadcast(29);
					rushUnderControl = rc.readBroadcast(29) <= rc.readBroadcast(36);
				}
				
				//Anti stuck
				if(rc.getRoundNum() < 100 && rc.getRoundNum() > 50 && rc.getTeamBullets() > 130){
					if(rc.isBuildReady()){
						tryBuildRobot(type.LUMBERJACK);
					}
				}
				
				//Gardener is builder
				if(gardenerIsBuilder){
					passiveMove();
					if(!holdForVictoryPoints){
						int buildOrder = rc.readBroadcast(50);
						if(buildOrder == 0){	
							gardenerBuild();				
						} else if(buildOrder == 1){
							if(rc.isBuildReady() && rc.getTeamBullets() > type.bulletCost){
								for (int i = 0; i < dirList.length; i++) {
									if (rc.canBuildRobot(rc.getType().SCOUT, dirList[i])) {
										rc.buildRobot(rc.getType().SCOUT, dirList[i]);
										rc.broadcast(50, 0);
										gardenerIsBuilder = false;
										targetLocation = new MapLocation(
								    			Math.round(rc.getLocation().x / (farmerSpacing * 2)) * farmerSpacing * 2 + farmerOffset,
								    			Math.round(rc.getLocation().y / (farmerSpacing * 2)) * farmerSpacing * 2 + farmerOffset
								    			);
									}
								}
							}
						} else if(buildOrder == 2){
							if(rc.isBuildReady() && rc.getTeamBullets() > type.bulletCost){
								for (int i = 0; i < dirList.length; i++) {
									if (rc.canBuildRobot(rc.getType().SOLDIER, dirList[i])) {
										rc.buildRobot(rc.getType().SOLDIER, dirList[i]);
										rc.broadcast(50, 1);
									}
								}
							}
						} else if(buildOrder == 3){
							if(rc.isBuildReady() && rc.getTeamBullets() > type.bulletCost){
								for (int i = 0; i < dirList.length; i++) {
									if (rc.canBuildRobot(rc.getType().LUMBERJACK, dirList[i])) {
										rc.buildRobot(rc.getType().LUMBERJACK, dirList[i]);
										rc.broadcast(50, 1);
									}
								}
							}
						}
					}
					rc.broadcast(3, rc.readBroadcast(3) + 1);
					
				//Gardener is farmer
				} else {
					switch (taskStage) {
	            	case 0:
	            		if(rc.readBroadcast(orderChannel) == 1){
	            			gardenerIsBuilder = true;
	            		} else if (!rushUnderControl){	//TODO Maybe REMOVE THIS! Might be bad
	            			gardenerBuild();
	            		}
	            		if(wander > 0){
	            			wander--;
	            			intelligentMove(targetDirection);
	            			break;
	            		} else {
	            			pickFarmLocation();
							if(currentLocation.distanceTo(targetLocation) < 0.04f) {
		            			taskStage = 1;
		            			rc.broadcast(28, rc.readBroadcast(28) - 1);
		                    } else {
		                    	targetDirection = currentLocation.directionTo(targetLocation);
		                    	float targetDistance = currentLocation.distanceTo(targetLocation);
		            			if(targetDistance < type.strideRadius){
		            				slugdrug(targetDirection, targetDistance);
		            			} else {
		            				slugdrug(targetDirection);
		            			}
			            		break;
		                    }
	                    }
	            	case 1:
	            		if(rc.readBroadcast(orderChannel) == 1){
	            			if(rc.isBuildReady() && rc.getTeamBullets() > BULLET_MARGIN) {
	            				for (Direction plantDirection: plantDirections) {
	            					if (rc.canBuildRobot(type.SOLDIER, plantDirection)) {
	            						rc.buildRobot(type.SOLDIER, plantDirection);
	            					}
	            				}
	            			}
	            		}
	            		if(rushUnderControl && rc.readBroadcast(orderChannel) != 1){
		            		for (Direction plantDirection: plantDirections) {
		            			if (rc.canPlantTree(plantDirection)) {
		                            rc.plantTree(plantDirection);
		                            break;
		                		}
		            		}
	            		}
	            		TreeInfo[] trees = rc.senseNearbyTrees(2.1f);
	            		MapLocation waterLocation = null;
	            		float treeHealth = 0x40;
	            		for (TreeInfo tree : trees) {
	            			if (tree.getHealth() < treeHealth) {
	            				treeHealth = tree.getHealth();
	            				waterLocation = tree.getLocation();
	            			}
	            		}
	            		if (waterLocation != null && rc.canWater(waterLocation)) {
	            			rc.water(waterLocation);
	            		}
	            		break;
	            	}
				}
				Clock.yield();
			} catch (Exception e) {
				System.out.println("Gardener Exception");
				e.printStackTrace();
			}
		}
	}
	
	private static void gardenerBuild(){
		try {
			//Panic build soldier
			if(!rushUnderControl || rc.readBroadcast(orderChannel) == 1){
				if(rc.isBuildReady()){
					tryBuildRobot(RobotType.SOLDIER);
				}
				return;
			}
			
			// Build Scout
			if (rc.readBroadcast(8) < INITIAL_SCOUT_BUILD_COUNT + rc.getRoundNum() / 600 && rc.isBuildReady()) {
				if(tryBuildRobot(RobotType.SCOUT)){
					rc.broadcast(8, rc.readBroadcast(8) + 1);
					return;
				}
			}
			
			//Build Lumberjack
			if(!screwEarlyLumberjacksFFSWeHavePlentyOfRoom){
				if (rc.isBuildReady() && rc.readBroadcast(7) < rc.readBroadcast(9)) {
					if(tryBuildRobot(RobotType.LUMBERJACK)){
						rc.broadcast(7, rc.readBroadcast(7) + 1);
						return;
					}
				}
			}
			
			// Build Soldier
//			if (rc.isBuildReady() && (tankNext < 1 || rc.getTreeCount() <= 12) && rc.readBroadcast(4) + rc.readBroadcast(5) >= BUILD_GARDENER_MAX_COUNT) {
//				if(tryBuildRobot(RobotType.SOLDIER)){
//					rc.broadcast(33, rc.readBroadcast(33) + 1);
//					tankNext++;
//				}
//			}
//			
//			if (rc.isBuildReady() && rc.getTreeCount() > 15 && rc.readBroadcast(4) + rc.readBroadcast(5) >= BUILD_GARDENER_MAX_COUNT){
//				if(tryBuildRobot(RobotType.TANK)){
//					tankNext = 0;
//				}
//			}
			
			if (rc.isBuildReady() && (rc.readBroadcastInt(34) < UNIT_DONATION_LIMIT || rc.readBroadcastBoolean(37)) && (rc.readBroadcast(27) >= rc.readBroadcast(34) || rc.getRoundNum() >= 500)) {
				double rand = random();
				if(rand < 0.92){
					tryBuildRobot(RobotType.SOLDIER);
					rc.broadcast(33, rc.readBroadcast(33) + 1);
				} else {
					tryBuildRobot(RobotType.LUMBERJACK);
				}
			}
			
			// Build Soldier
//			if(rc.getTreeCount() < 30){
//				if (rc.isBuildReady() && (rc.readBroadcastInt(34) < UNIT_DONATION_LIMIT || rc.readBroadcastBoolean(37)) && (rc.readBroadcast(27) >= rc.readBroadcast(34) || rc.getRoundNum() >= 500)) {
//					double rand = random();
//					if(rand < 0.92){
//						tryBuildRobot(RobotType.SOLDIER);
//						rc.broadcast(33, rc.readBroadcast(33) + 1);
//					} else {
//						tryBuildRobot(RobotType.LUMBERJACK);
//					}
//				}
//			} else if (rc.isBuildReady()) {
//					tryBuildRobot(RobotType.TANK);
//					rc.broadcast(33, rc.readBroadcast(33) + 3);
//			}
		} catch (Exception e) {
			System.out.println("Gardener Exception");
			e.printStackTrace();
		}
	}

	
	private static void pickFarmLocation() {
		try{
    		float x_r = rc.readBroadcastFloat(998);
    		float x_l = rc.readBroadcastFloat(999);
    		float y_t =  rc.readBroadcastFloat(996);
    		float y_b = rc.readBroadcastFloat(997);
	    	if (freeSpace(targetLocation, 1f) > 0) {
	    		if((x_r != -1f && targetLocation.x > x_r - 2) || (x_l != 10000f && targetLocation.x < x_l + 2) 
	    				|| (y_t != -1f && targetLocation.y > y_t - 2)  || (y_b != 10000f && targetLocation.y < y_b + 2)){
	    		} else {
	    			return;
	    		}
	    	}
	    	//boolean foundLocation = false;
			MapLocation location = new MapLocation(
		    		Math.round(rc.getLocation().x / (farmerSpacing * 2)) * farmerSpacing * 2 + farmerOffset,
		    		Math.round(rc.getLocation().y / (farmerSpacing * 2)) * farmerSpacing * 2 + farmerOffset
		    		);
			int counter = (int)(6 * (Math.random()));
		    for (int i = 0; i < 6; i++) {
		    	Direction direction  = farmDirections[counter];
		    	MapLocation newLocation = location.add(direction, farmerSpacing);
		    	if((x_r != -1f && newLocation.x > x_r - 2) || (x_l != 10000f && newLocation.x < x_l + 2)
		    			|| (y_t != -1f && newLocation.y > y_t - 2)  || (y_b != 10000f && newLocation.y < y_b + 2)){
		    		continue;
		    	} else {
			    	if (freeSpace(newLocation, 1.0f) > 0) {
			    		targetLocation = newLocation;
			    		return;
			    	}		   	
			   	}
		    	counter = ++counter % 6;
		    }
		    wander = 10;
		    targetDirection = targetDirection.opposite();
	    } catch (Exception e) {
				e.printStackTrace();
			}
	 }
	 
	private static int freeSpace(MapLocation location, float radius) {
		 try {
			RobotInfo[] robots = rc.senseNearbyRobots();
		    TreeInfo[] trees = rc.senseNearbyTrees();
		    if (rc.canSenseAllOfCircle(location, radius)) {
		    	for (RobotInfo robot : robots) {
		    		if (robot.getLocation().distanceTo(location) < robot.getRadius() + radius) {
		    			return 0;
		    		}
		    	}
				for (TreeInfo tree : trees) {
		    		if (tree.getLocation().distanceTo(location) < tree.getRadius() + radius) {
		    			return 0;
		    		}
		    	}
	    	} else {
	    		return 2;
	    	}
		 } catch (Exception e) {
			 System.out.println("Soldier Exception");
			 e.printStackTrace();
		 }
		 return 1;
	}
		 

	@SuppressWarnings("static-access")
	private static void runSoldier() throws GameActionException {
		while (true) {
			try {
				clearEnemyArchonChannels();
				checkVictoryPoints();
				currentLocation = rc.getLocation();
				//report alive
				rc.broadcast(34, rc.readBroadcast(34) + 1);
				
				//Check for enemies and move accordingly
				enemyRobots = rc.senseNearbyRobots(-1, enemyTeam);
				if (enemyRobots.length > 0) {
                	iAmUnderAttack();
                	RobotInfo enemyRobot = enemyRobots[0];                		
                	if(enemyRobot.getType() == type.ARCHON){
                		compromiseArchon(enemyRobot);   
                		if(enemyRobots.length > 1 && rc.getRoundNum() < 350){
                			enemyRobot = enemyRobots[1];
                		}
                	}
                	if(rc.getRoundNum() >= 350 || enemyRobot.getType() != type.ARCHON){
                		soldierMoveAndShootSequence(enemyRobot);
                	} else {
                		intelligentMove(casualDirection);
                	}
	                //No enemy found -> go to enemy archon locations
				} else {
	            	 boolean hasTarget = false;
	            	 if(rc.getRoundNum() - rc.readBroadcast(32) < 15){
	            		 hasTarget = true;
	            		 targetLocation = new MapLocation(rc.readBroadcastFloat(30), rc.readBroadcastFloat(31));
	            		 if(currentLocation.distanceTo(targetLocation) > 0.01f){
	            			 targetDirection = currentLocation.directionTo(targetLocation);
	            		 }
	            	 } else {
		            	 for(int i = 11; i < 23; i += 4){
		            		 if(rc.readBroadcast(i) != 0){
		            			 if(currentLocation.distanceTo(targetLocation) > 1f){
			            			 targetDirection = currentLocation.directionTo(new MapLocation(rc.readBroadcastFloat(i + 1), rc.readBroadcastFloat(i + 2)));
			            			 hasTarget = true;
			            			 break;
		            			 }
		            		 }
		            	 }
	            	 }
	            	 if(hasTarget){
	            		 intelligentMove(targetDirection);
	            	 } else {
	            		 if(rc.getRoundNum() < 500){
	            			 intelligentMove(casualDirection);
	            		 } else {
		            		 bounce();
		            		 intelligentMove(casualDirection);
	            		 }
	            	 }
	             }
				Clock.yield();
			} catch (Exception e) {
				System.out.println("Soldier Exception");
				e.printStackTrace();
			}
		}
	}

	private static void runLumberjack() throws GameActionException {
		while (true) {
			 try {
				 checkVictoryPoints();
				 currentLocation = rc.getLocation();
				 scanMapEdges();

				 enemyRobots = rc.senseNearbyRobots(4f, enemyTeam);
	             trees = rc.senseNearbyTrees(-1);
	             if (enemyRobots.length > 0) {
	            	 iAmUnderAttack();
	            	 RobotInfo enemyRobot = enemyRobots[0];
			         targetLocation = enemyRobot.getLocation();
			         targetDirection = currentLocation.directionTo(targetLocation);
			         if(currentLocation.distanceTo(targetLocation) < type.strideRadius){
			         	 tryMove(targetDirection, 20, 3, currentLocation.distanceTo(targetLocation) - type.bodyRadius - enemyRobot.getType().bodyRadius - 0.01f);
			         } else {
			           	 intelligentMove(targetDirection);
			         }
			         if (currentLocation.distanceTo(targetLocation) < GameConstants.LUMBERJACK_STRIKE_RADIUS + enemyRobot.getRadius() && rc.canStrike()) {
			           	 rc.strike();
			         } else if(trees.length > 0){
		        		 if (rc.canChop(trees[0].getID())) {
			            	 rc.chop(trees[0].getID());
			             }
		        	 }
	             } else if(trees.length > 0){
	            	 boolean hasTarget = false;
		             for(TreeInfo tree: trees){
			             if(!tree.getTeam().isPlayer()){
			            	 System.out.println(tree.getID());
				             targetLocation = tree.getLocation();
				             targetDirection = currentLocation.directionTo(targetLocation);
				             System.out.println(GameConstants.INTERACTION_DIST_FROM_EDGE + tree.getRadius());
				             System.out.println((currentLocation.distanceTo(targetLocation)));
				             if(currentLocation.distanceTo(targetLocation) - type.bodyRadius < GameConstants.INTERACTION_DIST_FROM_EDGE + tree.getRadius()){
				            	 //tryMove(targetDirection, 20, 3, currentLocation.distanceTo(targetLocation) - type.bodyRadius - tree.getRadius() - 0.01f);
				            	 System.out.println("not moving");
				             } else {
				            	 System.out.println("moving");
				            	 intelligentMove(targetDirection);
				             }      
				             if (rc.canChop(tree.getID())) {
				            	 rc.chop(tree.getID());
				             }
				             hasTarget = true;
				             break;
			             }
		             }
		             if(!hasTarget){
		            	 intelligentMove(targetDirection);
		             }
	             } else {
	            	 intelligentMove(targetDirection);
	             }
	             Clock.yield();
	            } catch (Exception e) {
	                e.printStackTrace();
	            }
		}
	}
	
	private static void runScout() throws GameActionException{
		while (true) {
			try {
				checkVictoryPoints();
				currentLocation = rc.getLocation();
				clearEnemyArchonChannels();
				
				//Check for many trees
				if(rc.getRoundNum() < 50 && mappedTrees.size() < 80){
					mappedTrees.addAll(Arrays.asList(rc.senseNearbyTrees(-1)));
					
				}
				if(rc.getRoundNum() == 60){
					if(mappedTrees.size() > 15){
						rc.broadcast(9, rc.readBroadcast(9) + max(mappedTrees.size() / 14, 6));
						
					}
				}
				
				//Check for gardeners, archons and trees containing bullets
				if(superTreeID == -1){
					//If no gardeners were found, look for trees containing bullets
					findTreeWithBullets();
					
					//if superIsSet = true look for the nearby superTreeID
				} else if((superTreeID != -1 && rc.canInteractWithTree(superTreeID))){
					if(rc.canShake() && rc.senseTree(superTreeID).getContainedBullets() > 0){
						rc.shake(superTreeID);
					}
					if(!findTreeWithBullets()){
						superTreeID = -1;
					}
				}
				
				if(superTreeID == -1){
					if(huntEnemies()){
						tryMove(targetDirection);
					} else {
						bounce();
						tryMove(casualDirection);
					}
				} else {
					intelligentMove(targetDirection);
				}
				
				Clock.yield();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
		
	@SuppressWarnings("static-access")
	private static void runTank() throws GameActionException{
		while (true) {
			try {
				clearEnemyArchonChannels();
				checkVictoryPoints();
				currentLocation = rc.getLocation();
				//report alive
				rc.broadcast(34, rc.readBroadcast(34) + 3);
				
				//Check for enemies and move accordingly
				enemyRobots = rc.senseNearbyRobots(-1, enemyTeam);
				if (enemyRobots.length > 0) {
                	iAmUnderAttack();
                	RobotInfo enemyRobot = enemyRobots[0];                		
                	if(enemyRobot.getType() == type.ARCHON){
                		compromiseArchon(enemyRobot);   
                		if(enemyRobots.length > 1 && rc.getRoundNum() < 350){
                			enemyRobot = enemyRobots[1];
                		}
                	}
                	if(rc.getRoundNum() >= 350 || enemyRobot.getType() != type.ARCHON){
                		tankMoveAndShootSequence(enemyRobot);
                	}
	                //No enemy found -> go to enemy archon locations
				} else {
	            	 boolean hasTarget = false;
	            	 if(rc.getRoundNum() - rc.readBroadcast(32) < 15){
	            		 hasTarget = true;
	            		 targetLocation = new MapLocation(rc.readBroadcastFloat(30), rc.readBroadcastFloat(31));
	            		 if(currentLocation.distanceTo(targetLocation) > 0.01f){
	            			 targetDirection = currentLocation.directionTo(targetLocation);
	            		 }
	            	 } else {
		            	 for(int i = 11; i < 23; i += 4){
		            		 if(rc.readBroadcast(i) != 0){
		            			 if(currentLocation.distanceTo(targetLocation) > 1f){
			            			 targetDirection = currentLocation.directionTo(new MapLocation(rc.readBroadcastFloat(i + 1), rc.readBroadcastFloat(i + 2)));
			            			 hasTarget = true;
			            			 break;
		            			 }
		            		 }
		            	 }
	            	 }
	            	 if(hasTarget){
	            		intelligentMove(targetDirection);
	            	 } else {
		            	intelligentMove(casualDirection);
	            	 }
	             }
				Clock.yield();
			} catch (Exception e) {
				System.out.println("Soldier Exception");
				e.printStackTrace();
			}
		}
	}
	
	private static void tankMoveAndShootSequence(RobotInfo enemyRobot){
		try {
			targetLocation = enemyRobot.getLocation();
	        targetDirection = currentLocation.directionTo(targetLocation);
	        
	        float distance = currentLocation.distanceTo(targetLocation);
	        if(distance < type.strideRadius){
	        	intelligentMove(targetDirection, distance - type.bodyRadius -  enemyRobot.getType().bodyRadius - 0.01f);
	        } else {
	        	intelligentMove(targetDirection);
	        }
	        
	        //Make sure no friendly bros or tree bros are in the line of fire
	        currentLocation = rc.getLocation();
	        float distanceToTarget = currentLocation.distanceTo(targetLocation);
	        float shootOffset = (float) (7* random()) - 3.5f;
	        if(distanceToTarget < 4f && (enemyRobot.getType() == type.ARCHON || enemyRobot.getType() == type.GARDENER) && rc.canFireTriadShot()){
	        	rc.fireTriadShot(currentLocation.directionTo(targetLocation).rotateLeftDegrees(shootOffset));
	        } else if(noFriendlyFire() && noTreeFire()){
	        	if(rc.canFirePentadShot()){
	        		rc.firePentadShot(currentLocation.directionTo(targetLocation).rotateLeftDegrees(shootOffset));
	        	} else if(rc.canFireTriadShot()){
	        		rc.fireTriadShot(currentLocation.directionTo(targetLocation).rotateLeftDegrees(shootOffset));
	        	} else if(rc.canFireSingleShot()){
	        		rc.fireSingleShot(currentLocation.directionTo(targetLocation));
	        	}
			}
		} catch (Exception e) {
			System.out.println("Soldier Exception");
			e.printStackTrace();
		}
	}
			
	private static void soldierMoveAndShootSequence(RobotInfo enemyRobot){
		try {
			targetLocation = enemyRobot.getLocation();
	        targetDirection = currentLocation.directionTo(targetLocation);
	        
	        float distance = currentLocation.distanceTo(targetLocation);
	        if(enemyRobot.getType() != type.GARDENER && enemyRobot.getType() != type.ARCHON){
	            if(distance <= 4f){
	            	intelligentMove(targetDirection.opposite());
	            } else {
		            intelligentMove(targetDirection);
	            }
	        } else{
	        	if(distance < type.strideRadius){
	        		intelligentMove(targetDirection, distance - type.bodyRadius -  enemyRobot.getType().bodyRadius - 0.01f);
	           } else {
	        	   intelligentMove(targetDirection);
	           }
	        }
	        //Make sure no friendly bros or tree bros are in the line of fire
	        currentLocation = rc.getLocation();
	        float distanceToTarget = currentLocation.distanceTo(targetLocation);
	        if(rc.getRoundNum() < 90) {
	        	if(enemyRobot.getType() != type.SCOUT && noTreeFire()){
	        		rc.broadcast(orderChannel, 1);
	        	}
	        }
	        float shootOffset = (float) (7* random()) - 3.5f;
	        if(distanceToTarget < 4f && (enemyRobot.getType() == type.ARCHON || enemyRobot.getType() == type.GARDENER) && rc.canFireTriadShot()){
	        	rc.fireTriadShot(currentLocation.directionTo(targetLocation).rotateLeftDegrees(shootOffset));
	        } else if(noFriendlyFire() && noTreeFire()){
	        	if(rc.canFirePentadShot()){
	        		rc.firePentadShot(currentLocation.directionTo(targetLocation).rotateLeftDegrees(shootOffset));
	        	} else if(rc.canFireTriadShot()){
	        		rc.fireTriadShot(currentLocation.directionTo(targetLocation).rotateLeftDegrees(shootOffset));
	        	} else if(rc.canFireSingleShot()){
	        		rc.fireSingleShot(currentLocation.directionTo(targetLocation));
	        	}
			}
		} catch (Exception e) {
			System.out.println("Soldier Exception");
			e.printStackTrace();
		}
	}
	
	

	//---------------------SCOUT METHODS---------------------//
	
	@SuppressWarnings("static-access")
	private static boolean huntEnemies() throws GameActionException{
		boolean foundTarget = false;	
		enemyRobots = rc.senseNearbyRobots(-1, enemyTeam);
		for(RobotInfo enemyRobot: enemyRobots){
			if (enemyRobot.getType() == type.ARCHON) {
				compromiseArchon(enemyRobot);
			} else if (enemyRobot.getType() == type.SOLDIER){
				targetDirection = currentLocation.directionTo(enemyRobot.getLocation()).opposite();
				foundTarget = true;
			}
		}
		return foundTarget;
	}
	
	private static void hugTarget(RobotInfo enemyRobot){
		targetLocation = enemyRobot.getLocation();
		targetDirection = new Direction(currentLocation, targetLocation);
		if(currentLocation.distanceTo(targetLocation) < type.strideRadius){
			moveLength = currentLocation.distanceTo(targetLocation) - type.bodyRadius - enemyRobot.getType().bodyRadius - 0.01f;
		} else {
			moveLength = type.strideRadius;
		}
		superTreeID = -1;
	}
	
	private static boolean findTreeWithBullets(){
		try {
			trees = rc.senseNearbyTrees(-1);
			for(TreeInfo tree: trees){
				if(tree.getContainedBullets() > 0){
					int treeID = tree.getID();
					if(rc.canShake() && rc.canInteractWithTree(treeID)){
						rc.shake(treeID);
					} else {
						targetLocation = tree.getLocation();
						targetDirection = new Direction(currentLocation, targetLocation);
						superTreeID = treeID;
						moveLength = type.strideRadius;
					}
					return true;
				}
			}
		} catch (GameActionException e) {
			e.printStackTrace();
		}	
		return false;
	}
	
	//---------------------END OF SCOUT METHODS---------------------//

	//---------------------MISC---------------------//
	
	private static void iAmUnderAttack(){
		try {
			if(enemyRobots.length > 0){
				rc.broadcastFloat(30, enemyRobots[0].getLocation().x);
				rc.broadcastFloat(31, enemyRobots[0].getLocation().y);
				rc.broadcast(32, rc.getRoundNum());
			}
		} catch (GameActionException e){
			e.printStackTrace();
		}
	}
	
	private static void detectRush(){
		try {
			if(enemyRobots.length > 0){
				int criticalRobots = 0;
				for(RobotInfo enemyRobot: enemyRobots){
					if(enemyRobot.getType() != type.GARDENER && enemyRobot.getType() != type.ARCHON && enemyRobot.getType() != type.LUMBERJACK){
						criticalRobots++;
					}
				}
				if(criticalRobots > 0){
					rc.broadcast(29, max(rc.readBroadcast(29), criticalRobots));
				}
			}
		} catch (GameActionException e){
			e.printStackTrace();
		}
	}
	
	private static void scanMapEdges(){
		float edgeCheckDist = type.sensorRadius - 0.1f;
		try {
			if (rc.readBroadcastFloat(998) == -1f && !rc.onTheMap(currentLocation.add(0, edgeCheckDist))) {
				MapLocation loc = currentLocation;
				float dist = edgeCheckDist / 2;
				float fraction = edgeCheckDist / 4;
				while(fraction > 0.0001){
					loc = currentLocation.add(0, dist);
					if(!rc.onTheMap(loc)){
						dist = dist - fraction;
					} else {
						dist = dist + fraction;
					}
					fraction /= 2;
				}
				rc.broadcastFloat(998, loc.x);
			}
			if (rc.readBroadcastFloat(999) == 10000f && !rc.onTheMap(currentLocation.add(PI, edgeCheckDist))) {
				MapLocation loc = currentLocation;
				float dist = edgeCheckDist / 2;
				float fraction = edgeCheckDist / 4;
				while(fraction > 0.0001){
					loc = currentLocation.add(PI, dist);
					if(!rc.onTheMap(loc)){
						dist = dist - fraction;
					} else {
						dist = dist + fraction;
					}
					fraction /= 2;
				}
				rc.broadcastFloat(999, loc.x);
			}
			if (rc.readBroadcastFloat(996) == -1f && !rc.onTheMap(currentLocation.add(PI/2, edgeCheckDist))) {
				MapLocation loc = currentLocation;
				float dist = edgeCheckDist / 2;
				float fraction = edgeCheckDist / 4;
				while(fraction > 0.0001){
					loc = currentLocation.add(PI/2, dist);
					if(!rc.onTheMap(loc)){
						dist = dist - fraction;
					} else {
						dist = dist + fraction;
					}
					fraction /= 2;
				}
				rc.broadcastFloat(996, loc.y);
			}
			if (rc.readBroadcastFloat(997) == 10000f && !rc.onTheMap(currentLocation.add(3 * PI / 2, edgeCheckDist))) {
				MapLocation loc = currentLocation;
				float dist = edgeCheckDist / 2;
				float fraction = edgeCheckDist / 4;
				while(fraction > 0.0001){
					loc = currentLocation.add(3 * PI / 2, dist);
					if(!rc.onTheMap(loc)){
						dist = dist - fraction;
					} else {
						dist = dist + fraction;
					}
					fraction /= 2;
				}
				rc.broadcastFloat(997, loc.y);
			}
		} catch (GameActionException e) {
			e.printStackTrace();
		}	
	}
	
	private static boolean checkVictoryPoints(){
    	try {
    		if(rc.readBroadcastBoolean(37) && rc.readBroadcastInt(34) > UNIT_DONATION_LIMIT && rc.getTeamBullets() > 200){
    			rc.donate(rc.getTeamBullets() - 200);
    			return true;
    		}
    		int roundNum = rc.getRoundNum();
    		if (rc.getTeamBullets() / rc.getVictoryPointCost() >= 1001 - rc.getTeamVictoryPoints() || roundNum > rc.getRoundLimit() - 30) {
    			rc.donate(rc.getTeamBullets());
    		}
		} catch (GameActionException e) {
			e.printStackTrace();
    	}
		return false;
	}
	
	
	//Recks enemy Archons xD
	private static void compromiseArchon(RobotInfo enemyRobot){
		try {
			int archonID = enemyRobot.getID();
			int j = 10;
			while(archonID != rc.readBroadcast(j) && rc.readBroadcast(j) != 0){
				j += 4;
			}
			if(rc.readBroadcast(j) == 0){
				rc.broadcast(j, archonID);
			}
			
			rc.broadcast(j + 1, rc.getRoundNum());
			rc.broadcastFloat(j + 2, enemyRobot.getLocation().x);
			rc.broadcastFloat(j + 3, enemyRobot.getLocation().y);
		} catch (GameActionException e) {
			e.printStackTrace();
		}
	}
	
	static void clearEnemyArchonChannels(){
		try {
			for(int i = 11; i < 23; i += 4){
				if(rc.readBroadcast(i) != 0 && rc.getRoundNum() - rc.readBroadcast(i) > ARCHON_TIMEOUT){
					rc.broadcast(i - 1, 0);
					rc.broadcast(i, 0);
					rc.broadcast(i + 1, 0);
					rc.broadcast(i + 2, 0);
				}
			}
		} catch (GameActionException e) {
			e.printStackTrace();
		}
	}
	
	//---------------------END MISC---------------------//
	
	//---------------------BUILDING---------------------//
	
	private static boolean tryBuildRobot(RobotType type) throws GameActionException {
		if (rc.getTeamBullets() > type.bulletCost + BULLET_MARGIN + rc.getTreeCount()) {
			for (int i = 0; i < dirList.length; i++) {
				if (rc.canBuildRobot(type, dirList[i])) {
					rc.buildRobot(type, dirList[i]);
					return true;
				}
			}
		}
		return false;
	}
	
	//---------------------END BUILDING---------------------//

	//---------------------MOVEMENT---------------------//	
	
	private static void scoutBounce(){
		try {
			if (!rc.canMove(casualDirection)) {
				float dx = casualDirection.getDeltaX(2.5f);
				if (!rc.canMove(new Direction(dx, 0), abs(dx))) {
					float x_r = rc.readBroadcastFloat(998);
					float x_l = rc.readBroadcastFloat(999);
					MapLocation testWith = currentLocation.add(casualDirection, 4f);
					if(!rc.onTheMap(testWith) &&
							((testWith.x > x_r && x_r == -1) ||
							(testWith.x < x_l && x_l == 10000))){
						int modifier = 0;
						if(x_r == -1 && testWith.x > x_r){
							modifier = 1;
						}
						MapLocation loc = currentLocation;
						for(int i = 1; i < 75; i++){
							loc = loc.add(modifier*PI, 0.05f);
							if(!rc.onTheMap(loc)){
								if(modifier == 1){
									rc.broadcastFloat(998, loc.x);
								} else {
									rc.broadcastFloat(999, loc.x);
								}
								break;
							}
						}
		    		}
					if (casualDirection.radians > 0) {
						casualDirection = new Direction(PI - casualDirection.radians);
					} else {
						casualDirection = new Direction(-PI + casualDirection.radians);
					}
				}
				if (!rc.canMove(new Direction(0,  casualDirection.getDeltaY(2.5f)), abs(casualDirection.getDeltaY(2.5f)))) {
					float y_r = rc.readBroadcastFloat(996);
					float y_l = rc.readBroadcastFloat(997);
					MapLocation testWith = currentLocation.add(casualDirection, 4f);
					if(!rc.onTheMap(testWith) &&
							((testWith.y > y_r && y_r == -1) ||
							(testWith.y < y_l && y_l == 10000))){
						int modifier = 3;
						if(y_r == -1 && testWith.x > y_r){
							modifier = 1;
						}
						MapLocation loc = currentLocation;
						for(int i = 1; i < 75; i++){
							loc = loc.add(modifier*PI/2, 0.05f);
							if(!rc.onTheMap(loc)){
								if(modifier == 1){
									rc.broadcastFloat(996, loc.y);
								} else {
									rc.broadcastFloat(997, loc.y);
								}
								break;
							}
						}
		    		}
					casualDirection = new Direction(casualDirection.radians * -1);
				}
			}
		} catch (GameActionException e) {
			e.printStackTrace();
		}	
	}
	
	private static void bounce(){
			if (!rc.canMove(casualDirection)) {
				float dx = casualDirection.getDeltaX(2.5f);
				if (!rc.canMove(new Direction(dx, 0), abs(dx))) {
					
					if (casualDirection.radians > 0) {
						casualDirection = new Direction(PI - casualDirection.radians);
					} else {
						casualDirection = new Direction(-PI + casualDirection.radians);
					}
				}
				float dy = casualDirection.getDeltaY(2.5f);
				if (!rc.canMove(new Direction(0, dy), abs(dy))) {
					casualDirection = new Direction(casualDirection.radians * -1);
				}
			}
	}
	
	private static Direction randomDirection() {
		return new Direction((float) random() * 2 * PI);
	}
	
	
	private static boolean tryMove(Direction dir) throws GameActionException {
		if (rc.canMove(dir)) {
			rc.move(dir);
			return true;
		}
		return false;
	}

	private static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide, float moveLength) throws GameActionException {

		// First, try intended direction
		if (rc.canMove(dir, moveLength)) {
			rc.move(dir, moveLength);
			return true;
		}

		// Now try a bunch of similar angles
		int currentCheck = 1;

		while (currentCheck <= checksPerSide) {
			// Try the offset of the left side
			if (rc.canMove(dir.rotateLeftDegrees(degreeOffset * currentCheck), moveLength)) {
				rc.move(dir.rotateLeftDegrees(degreeOffset * currentCheck), moveLength);
				return true;
			}
			// Try the offset on the right side
			if (rc.canMove(dir.rotateRightDegrees(degreeOffset * currentCheck), moveLength)) {
				rc.move(dir.rotateRightDegrees(degreeOffset * currentCheck), moveLength);
				return true;
			}
			// No move performed, try slightly further
			currentCheck++;
		}

		// A move never happened, so return false.
		return false;
	}
	
	
	private static void intelligentMove(Direction dir) {
		intelligentMove(dir, type.strideRadius);
	}
	
	private static void intelligentMove(MapLocation loc) {
		if(currentLocation.distanceTo(loc) > 0.01f){
			intelligentMove(currentLocation.directionTo(loc), type.strideRadius);
		}
	}

	//No bulletdodge here, so that they don't run into their own shots
	private static void intelligentMove(Direction dir, float moveLength){
		try {
			bullets = rc.senseNearbyBullets(8);
			if (bullets.length > 0) {
				tryMove(advancedBulletDodge(dir, bullets), 20, 3, type.strideRadius);
			} else {
//				tryMove(dir, 15, 5, moveLength);
				slugdrug(dir, moveLength);
			}
		} catch (GameActionException e) {
			e.printStackTrace();
		}
	}
	
	//---------------------END MOVEMENT---------------------//
	
	//---------------------BULLET DODGING---------------------//
	
	/*
	 * Don't use this shiet for scout, they have to great bullet detection and will just act weird
	 * checks  x x x
	 * 		  x -O- x
	 *         x   x, picks the location of the 7 that is safe
	 */
	private static Direction advancedBulletDodge(Direction dir, BulletInfo[] bullets) {
		Direction potentialDirection = dir;
		TreeMap<Integer, Direction> weights = new TreeMap<>();
		int weight;
		for(int i = 0; i < 3; i = i > 0 ? i*-1 : --i*-1){
			weight = 0;
			potentialDirection = dir.rotateLeftRads(i*PI/4);
			if(!rc.canMove(potentialDirection)){
				continue;
			}
			for(BulletInfo bullet: bullets){
				MapLocation potentialLoc = currentLocation.add(potentialDirection, type.strideRadius);
				Direction bulletDir = bullet.getDir();
				MapLocation bulletLoc = bullet.getLocation();
	
				float distance = bulletLoc.distanceTo(potentialLoc);
				Direction fromBulletDir = bulletLoc.directionTo(potentialLoc);

				float hitAngle = bulletDir.radiansBetween(fromBulletDir);
	
				if (abs(hitAngle) > PI / 2) {
					continue;
				}
	
				float perpendicularDistance = (float) abs(distance * sin(hitAngle));
				if (perpendicularDistance <= type.bodyRadius) {
					if(potentialLoc.distanceTo(bulletLoc) < 4){
						weight += 2;
					} else {
						weight++;
					}
				}
				if(Clock.getBytecodesLeft() < 500){
					break;
				}
			}
			if(weight == 0){
				return potentialDirection;
			}
			weights.put(weight, potentialDirection);
			if(Clock.getBytecodesLeft() < 500){
				break;
			}
		}
		if(!weights.isEmpty()){
			return weights.pollFirstEntry().getValue();
		}
		return dir;
	}

	private static Direction bulletDodge(Direction dir, BulletInfo[] bullets) {
		for (BulletInfo bullet: bullets) {
			Direction bDir = bullet.getDir();
			MapLocation bLoc = bullet.getLocation();

			float distance = bLoc.distanceTo(currentLocation);
			Direction fromBulletDir = bLoc.directionTo(currentLocation);
			float hitAngle = bDir.radiansBetween(fromBulletDir);
			if (abs(hitAngle) > PI / 2) {
				continue;
			}

			float perpendicularDistance = (float) abs(distance * sin(hitAngle));

			// if true then bullet will collide -> we must dodge
			if (perpendicularDistance <= type.bodyRadius) {
				Direction newDir = bullet.getDir().rotateLeftRads(PI / 2);
				if (rc.canMove(newDir)) {
					return newDir;
				} else if (rc.canMove(newDir.rotateLeftRads(PI))) {
					return newDir.rotateLeftRads(PI);
				}
			}
		}
		return dir;
	}
	
	//---------------------DODGING---------------------//
	
	//---------------------SHOOTING---------------------//
	
	//Not 100 % this works, but I think so
	private static boolean noFriendlyFire(){
        friendlyRobots = rc.senseNearbyRobots(5, rc.getTeam());
		for(RobotInfo friendlyRobot: friendlyRobots){
			MapLocation friendlyLocation = friendlyRobot.getLocation();
			float criticalAngle = abs(currentLocation.directionTo(friendlyLocation).radiansBetween(targetDirection));
			if(criticalAngle > PI / 2){
				continue;
			}
			float distanceToFriendly = currentLocation.distanceTo(friendlyLocation);
			float criticalDistance = (float) (distanceToFriendly * sin(criticalAngle));
			if(criticalDistance < friendlyRobot.getRadius() + 0.8f && distanceToFriendly < currentLocation.distanceTo(targetLocation)){
				return false;
			}
		}
		return true;
	}
	
	private static boolean noTreeFire(){
		if(rc.getTreeCount() >= 30){
			return true;
		}
        trees = rc.senseNearbyTrees(6);
		for(TreeInfo tree: trees){
			if(tree.getTeam() != enemyTeam){
				MapLocation treeLocation = tree.getLocation();
				float criticalAngle = abs(currentLocation.directionTo(treeLocation).radiansBetween(targetDirection));
				if(criticalAngle > PI / 2){
					continue;
				}
				float distanceToTree = currentLocation.distanceTo(treeLocation);
				float criticalDistance = (float) (distanceToTree * sin(criticalAngle));
				if(criticalDistance < tree.getRadius() + 0.2f && distanceToTree < currentLocation.distanceTo(targetLocation)){
					return false;
				}
			}
		}
		return true;
	}
	
	//---------------------END SHOOTING---------------------//
	
	//---------------------SORTING---------------------//

//	private static TreeMap<Float, BodyInfo> sortByDistance(BodyInfo[] bodies) {
//		TreeMap<Float, BodyInfo> bodyMap = new TreeMap<>();
//		for (BodyInfo body : bodies) {
//			bodyMap.put(rc.getLocation().distanceTo(body.getLocation()), body);
//		}
//		return bodyMap;
//	}
	
	//---------------------END SORTING---------------------//
	
	
	static LinkedList<MapLocation> oldLocations = new LinkedList<>();
	private static boolean slugdrug(MapLocation pathing_target) throws GameActionException {
		if(currentLocation.distanceTo(pathing_target) < 0.01f){
			return true;
		}
		return slugdrug(currentLocation.directionTo(pathing_target), currentLocation.distanceTo(pathing_target));
	}
	
	private static boolean slugdrug(Direction toMove) throws GameActionException {
		return slugdrug(toMove, type.strideRadius);
	}
	
	private static boolean slugdrug(Direction toMove, float distance) throws GameActionException {
		if (distance < 0.01f) {
			return true;
		}
		MapLocation ourLoc = currentLocation;
		MapLocation pathing_target = null;
		Direction[] toTry = {
				toMove.rotateRightRads(0),
				toMove.rotateRightRads(PI/4), 
				toMove.rotateRightRads(-PI/4), 
				toMove.rotateRightRads(PI/2), 
				toMove.rotateRightRads(-PI/2), 
				toMove.rotateRightRads(3*PI/4), 
				toMove.rotateRightRads(-3*PI/4), 
				toMove.rotateRightRads(PI)
		};
		
		for (Direction d : toTry) {
			if (rc.canMove(d, distance)) {
				MapLocation newLocation = ourLoc.add(d, distance);
				boolean sluged = false;
				for(MapLocation slug : oldLocations) {
					if (newLocation.distanceTo(slug) < distance * 0.7f) { //why ^2?
						sluged = true;
						break;
					}
				}
				
				if (!sluged) {
					pathing_target = newLocation;
					oldLocations.addFirst(pathing_target);
					if (oldLocations.size() > 40) {
						oldLocations.removeLast();
					}
					if (rc.canMove(pathing_target)) {
						rc.move(pathing_target);
						return true;
					}
					break;
				}
			}
		}
		oldLocations = new LinkedList<>();
		oldLocations.add(ourLoc);
		return false;
	}
}
