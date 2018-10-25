package tom;
import battlecode.common.*;

public strictfp class RobotPlayer {
    private static RobotController rc;
    
    //HEJ
    
    private static int taskStage;
    private static MapLocation taskLocation;
  //  private static Direction taskDirection;

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
    **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;
        

        // Here, we've separated the controls into a different method for each RobotType.
        // You can add the missing ones or rewrite this into your own control structure.
        switch (rc.getType()) {
            case ARCHON:
            	taskStage = 0;
            	//taskDirection = Direction.getEast();
            	pickGardenerSpawnLocation();
                runArchon();
                break;
            case GARDENER:
            	taskStage = 0;
            	taskLocation = rc.getLocation();
                runGardener();
                break;
            case SOLDIER:
                runSoldier();
                break;
            case LUMBERJACK:
                runLumberjack();
                break;
        }
	}
    
    static void pickGardenerSpawnLocation() {
    	MapLocation location = new MapLocation(
    			Math.round(rc.getLocation().x / (farmerSpacing * 2)) * farmerSpacing * 2 + farmerOffset,
    			Math.round(rc.getLocation().y / (farmerSpacing * 2)) * farmerSpacing * 2 + farmerOffset
    			);
    	System.out.println(location);
    	if (freeSpace(location, 1.0f) == 1) {
    		taskLocation = location;
    		return;
    	}
    	for (Direction direction : farmDirections) {
    		MapLocation location_asd = location.add(direction, farmerSpacing);
    		if (freeSpace(location_asd, 1.0f) == 1) {
        		taskLocation = location_asd;
        		return;
        	}
    	}
    
    }

    static void runArchon() throws GameActionException {
        while (true) {
            try {
            	if (rc.getTeamBullets() >= 10000 || rc.getRoundNum() == rc.getRoundLimit() - 1) {
            		rc.donate(rc.getTeamBullets());
            	}
            	
            	//MapLocation moveTo = taskLocation.add(taskLocation.directionTo(rc.getLocation()), 3f);
            	MapLocation moveTo = taskLocation.add(Direction.getNorth(), 3f);
            	
            	switch (taskStage) {
            	case 0:
            		if (rc.getLocation().distanceTo(moveTo) < 0.01f) {
            			taskStage = 1;
                    } else {
                    	if (rc.canMove(moveTo)) {
                			rc.move(moveTo);
                		} else {
                        	Clock.yield();
                        }
                    }
                    break;
            	case 1:
            		Direction gardenerPlacement = rc.getLocation().directionTo(taskLocation);
            		if (rc.canHireGardener(gardenerPlacement) && rc.getTeamBullets() >= 120) {
                        rc.hireGardener(gardenerPlacement);
                        pickGardenerSpawnLocation();
                        taskStage = 0;
            		} else {
            			Clock.yield();
            		}
            		break;
            	}
            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }
    }
    

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
    static final float farmerSpacing = 5.32f;
    static final float farmerOffset = 0;

	static void runGardener() throws GameActionException {

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
            	switch (taskStage) {
            	case 0:
            		if (rc.getLocation().distanceTo(taskLocation) < 0.01f) {
            			taskStage = 1;
                    } else {
                    	if (rc.canMove(taskLocation)) {
                			rc.move(taskLocation);
                		} else {
                        	Clock.yield();
                        }
                    }
                    break;
            	case 1:
//            		TODO: check for condition to place new tree?
            		for (Direction plantDirection: plantDirections) {
            			if (rc.canPlantTree(plantDirection)) {
                            rc.plantTree(plantDirection);
                            break;
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
            		Clock.yield();
            		break;
            		}
            		
            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

    static void runSoldier() throws GameActionException {
        Team enemy = rc.getTeam().opponent();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                MapLocation myLocation = rc.getLocation();

                // See if there are any nearby enemy robots
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                // If there are some...
                if (robots.length > 0) {
                    // And we have enough bullets, and haven't attacked yet this turn...
                    if (rc.canFireSingleShot()) {
                        // ...Then fire a bullet in the direction of the enemy.
                        rc.fireSingleShot(rc.getLocation().directionTo(robots[0].location));
                    }
                }

                // Move randomly
                tryMove(randomDirection());

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

    static void runLumberjack() throws GameActionException {
        System.out.println("I'm a lumberjack!");
        Team enemy = rc.getTeam().opponent();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
                RobotInfo[] robots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius+GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);

                if(robots.length > 0 && !rc.hasAttacked()) {
                    // Use strike() to hit all nearby robots!
                    rc.strike();
                } else {
                    // No close robots, so search for robots within sight radius
                    robots = rc.senseNearbyRobots(-1,enemy);

                    // If there is a robot, move towards it
                    if(robots.length > 0) {
                        MapLocation myLocation = rc.getLocation();
                        MapLocation enemyLocation = robots[0].getLocation();
                        Direction toEnemy = myLocation.directionTo(enemyLocation);

                        tryMove(toEnemy);
                    } else {
                        // Move Randomly
                        tryMove(randomDirection());
                    }
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Lumberjack Exception");
                e.printStackTrace();
            }
        }
    }

    /**
     * Returns a random Direction
     * @return a random Direction
     */
    static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles directly in the path.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        return tryMove(dir,20,3);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles direction in the path.
     *
     * @param dir The intended direction of movement
     * @param degreeOffset Spacing between checked directions (degrees)
     * @param checksPerSide Number of extra directions checked on each side, if intended direction was unavailable
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {

        // First, try intended direction
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }

        // Now try a bunch of similar angles
        boolean moved = false;
        int currentCheck = 1;

        while(currentCheck<=checksPerSide) {
            // Try the offset of the left side
            if(rc.canMove(dir.rotateLeftDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateLeftDegrees(degreeOffset*currentCheck));
                return true;
            }
            // Try the offset on the right side
            if(rc.canMove(dir.rotateRightDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateRightDegrees(degreeOffset*currentCheck));
                return true;
            }
            // No move performed, try slightly further
            currentCheck++;
        }

        // A move never happened, so return false.
        return false;
    }

    /**
     * A slightly more complicated example function, this returns true if the given bullet is on a collision
     * course with the current robot. Doesn't take into account objects between the bullet and this robot.
     *
     * @param bullet The bullet in question
     * @return True if the line of the bullet's path intersects with this robot's current position.
     */
    static boolean willCollideWithMe(BulletInfo bullet) {
        MapLocation myLocation = rc.getLocation();

        // Get relevant bullet information
        Direction propagationDirection = bullet.dir;
        MapLocation bulletLocation = bullet.location;

        // Calculate bullet relations to this robot
        Direction directionToRobot = bulletLocation.directionTo(myLocation);
        float distToRobot = bulletLocation.distanceTo(myLocation);
        float theta = propagationDirection.radiansBetween(directionToRobot);

        // If theta > 90 degrees, then the bullet is traveling away from us and we can break early
        if (Math.abs(theta) > Math.PI/2) {
            return false;
        }

        // distToRobot is our hypotenuse, theta is our angle, and we want to know this length of the opposite leg.
        // This is the distance of a line that goes from myLocation and intersects perpendicularly with propagationDirection.
        // This corresponds to the smallest radius circle centered at our location that would intersect with the
        // line that is the path of the bullet.
        float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)

        return (perpendicularDist <= rc.getType().bodyRadius);
    }
    
    /**
     * 
     * @param location
     * @param radius
     * @return 0 false, 1 true, 2 unknown
     */
    static int freeSpace(MapLocation location, float radius) {
    	int value = 1;
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
    	} else if (rc.canSensePartOfCircle(location, radius)) {
    		value = 2;
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
    	return value;
    }
}
